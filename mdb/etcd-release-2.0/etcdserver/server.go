// Copyright 2015 CoreOS, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package etcdserver

import (
	"encoding/json"
	"expvar"
	"fmt"
	"log"
	"math/rand"
	"net/http"
	"path"
	"regexp"
	"sync/atomic"
	"time"

	"github.com/coreos/etcd/discovery"
	"github.com/coreos/etcd/etcdserver/etcdhttp/httptypes"
	pb "github.com/coreos/etcd/etcdserver/etcdserverpb"
	"github.com/coreos/etcd/etcdserver/stats"
	"github.com/coreos/etcd/pkg/fileutil"
	"github.com/coreos/etcd/pkg/idutil"
	"github.com/coreos/etcd/pkg/metrics"
	"github.com/coreos/etcd/pkg/pbutil"
	"github.com/coreos/etcd/pkg/timeutil"
	"github.com/coreos/etcd/pkg/types"
	"github.com/coreos/etcd/pkg/wait"
	"github.com/coreos/etcd/raft"
	"github.com/coreos/etcd/raft/raftpb"
	"github.com/coreos/etcd/rafthttp"
	"github.com/coreos/etcd/snap"
	"github.com/coreos/etcd/store"
	"github.com/coreos/etcd/version"
	"github.com/coreos/etcd/wal"

	"github.com/coreos/etcd/Godeps/_workspace/src/golang.org/x/net/context"
)

const (
	// owner can make/remove files inside the directory
	privateDirMode = 0700

	defaultSyncTimeout = time.Second
	DefaultSnapCount   = 10000
	// TODO: calculate based on heartbeat interval
	defaultPublishRetryInterval = 5 * time.Second

	StoreAdminPrefix = "/0"
	StoreKeysPrefix  = "/1"

	purgeFileInterval = 30 * time.Second
)

var (
	storeMembersPrefix        = path.Join(StoreAdminPrefix, "members")
	storeRemovedMembersPrefix = path.Join(StoreAdminPrefix, "removed_members")

	storeMemberAttributeRegexp = regexp.MustCompile(path.Join(storeMembersPrefix, "[[:xdigit:]]{1,16}", attributesSuffix))
)

func init() {
	rand.Seed(time.Now().UnixNano())
}

type Response struct {
	Event   *store.Event
	Watcher store.Watcher
	err     error
}

type Server interface {
	// Start performs any initialization of the Server necessary for it to
	// begin serving requests. It must be called before Do or Process.
	// Start must be non-blocking; any long-running server functionality
	// should be implemented in goroutines.
	Start()
	// Stop terminates the Server and performs any necessary finalization.
	// Do and Process cannot be called after Stop has been invoked.
	Stop()
	// ID returns the ID of the Server.
	ID() types.ID
	// Leader returns the ID of the leader Server.
	Leader() types.ID
	// Do takes a request and attempts to fulfill it, returning a Response.
	Do(ctx context.Context, r pb.Request) (Response, error)
	// Process takes a raft message and applies it to the server's raft state
	// machine, respecting any timeout of the given context.
	Process(ctx context.Context, m raftpb.Message) error
	// AddMember attempts to add a member into the cluster. It will return
	// ErrIDRemoved if member ID is removed from the cluster, or return
	// ErrIDExists if member ID exists in the cluster.
	AddMember(ctx context.Context, memb Member) error
	// RemoveMember attempts to remove a member from the cluster. It will
	// return ErrIDRemoved if member ID is removed from the cluster, or return
	// ErrIDNotFound if member ID is not in the cluster.
	RemoveMember(ctx context.Context, id uint64) error

	// UpdateMember attempts to update a existing member in the cluster. It will
	// return ErrIDNotFound if the member ID does not exist.
	UpdateMember(ctx context.Context, updateMemb Member) error
}

// EtcdServer is the production implementation of the Server interface
type EtcdServer struct {
	cfg *ServerConfig

	r raftNode

	w          wait.Wait
	stop       chan struct{}
	done       chan struct{}
	errorc     chan error
	id         types.ID
	attributes Attributes

	Cluster *Cluster

	store store.Store

	stats  *stats.ServerStats
	lstats *stats.LeaderStats

	SyncTicker <-chan time.Time

	reqIDGen *idutil.Generator
}

// NewServer creates a new EtcdServer from the supplied configuration. The
// configuration is considered static for the lifetime of the EtcdServer.
func NewServer(cfg *ServerConfig) (*EtcdServer, error) {
	st := store.New(StoreAdminPrefix, StoreKeysPrefix)
	var w *wal.WAL
	var n raft.Node
	var s *raft.MemoryStorage
	var id types.ID

	// Run the migrations.
	dataVer, err := version.DetectDataDir(cfg.DataDir)
	if err != nil {
		return nil, err
	}
	if err := upgradeDataDir(cfg.DataDir, cfg.Name, dataVer); err != nil {
		return nil, err
	}

	haveWAL := wal.Exist(cfg.WALDir())
	ss := snap.New(cfg.SnapDir())

	var remotes []*Member
	switch {
	case !haveWAL && !cfg.NewCluster:
		if err := cfg.VerifyJoinExisting(); err != nil {
			return nil, err
		}
		existingCluster, err := GetClusterFromRemotePeers(getRemotePeerURLs(cfg.Cluster, cfg.Name), cfg.Transport)
		if err != nil {
			return nil, fmt.Errorf("cannot fetch cluster info from peer urls: %v", err)
		}
		if err := ValidateClusterAndAssignIDs(cfg.Cluster, existingCluster); err != nil {
			return nil, fmt.Errorf("error validating peerURLs %s: %v", existingCluster, err)
		}
		remotes = existingCluster.Members()
		cfg.Cluster.SetID(existingCluster.id)
		cfg.Cluster.SetStore(st)
		cfg.Print()
		id, n, s, w = startNode(cfg, nil)
	case !haveWAL && cfg.NewCluster:
		if err := cfg.VerifyBootstrap(); err != nil {
			return nil, err
		}
		m := cfg.Cluster.MemberByName(cfg.Name)
		if isMemberBootstrapped(cfg.Cluster, cfg.Name, cfg.Transport) {
			return nil, fmt.Errorf("member %s has already been bootstrapped", m.ID)
		}
		if cfg.ShouldDiscover() {
			str, err := discovery.JoinCluster(cfg.DiscoveryURL, cfg.DiscoveryProxy, m.ID, cfg.Cluster.String())
			if err != nil {
				return nil, err
			}
			if cfg.Cluster, err = NewClusterFromString(cfg.Cluster.token, str); err != nil {
				return nil, err
			}
			if err := cfg.Cluster.Validate(); err != nil {
				return nil, fmt.Errorf("bad discovery cluster: %v", err)
			}
		}
		cfg.Cluster.SetStore(st)
		cfg.PrintWithInitial()
		id, n, s, w = startNode(cfg, cfg.Cluster.MemberIDs())
	case haveWAL:
		if err := fileutil.IsDirWriteable(cfg.DataDir); err != nil {
			return nil, fmt.Errorf("cannot write to data directory: %v", err)
		}

		if err := fileutil.IsDirWriteable(cfg.MemberDir()); err != nil {
			return nil, fmt.Errorf("cannot write to member directory: %v", err)
		}

		if cfg.ShouldDiscover() {
			log.Printf("etcdserver: discovery token ignored since a cluster has already been initialized. Valid log found at %q", cfg.WALDir())
		}
		snapshot, err := ss.Load()
		if err != nil && err != snap.ErrNoSnapshot {
			return nil, err
		}
		if snapshot != nil {
			if err := st.Recovery(snapshot.Data); err != nil {
				log.Panicf("etcdserver: recovered store from snapshot error: %v", err)
			}
			log.Printf("etcdserver: recovered store from snapshot at index %d", snapshot.Metadata.Index)
		}
		cfg.Cluster = NewClusterFromStore(cfg.Cluster.token, st)
		cfg.Print()
		if snapshot != nil {
			log.Printf("etcdserver: loaded cluster information from store: %s", cfg.Cluster)
		}
		if !cfg.ForceNewCluster {
			id, n, s, w = restartNode(cfg, snapshot)
		} else {
			id, n, s, w = restartAsStandaloneNode(cfg, snapshot)
		}
	default:
		return nil, fmt.Errorf("unsupported bootstrap config")
	}

	sstats := &stats.ServerStats{
		Name: cfg.Name,
		ID:   id.String(),
	}
	sstats.Initialize()
	lstats := stats.NewLeaderStats(id.String())

	srv := &EtcdServer{
		cfg:    cfg,
		errorc: make(chan error, 1),
		store:  st,
		r: raftNode{
			Node:        n,
			snapCount:   cfg.SnapCount,
			ticker:      time.Tick(time.Duration(cfg.TickMs) * time.Millisecond),
			raftStorage: s,
			storage:     NewStorage(w, ss),
		},
		id:         id,
		attributes: Attributes{Name: cfg.Name, ClientURLs: cfg.ClientURLs.StringSlice()},
		Cluster:    cfg.Cluster,
		stats:      sstats,
		lstats:     lstats,
		SyncTicker: time.Tick(500 * time.Millisecond),
		reqIDGen:   idutil.NewGenerator(uint8(id), time.Now()),
	}

	// TODO: move transport initialization near the definition of remote
	tr := rafthttp.NewTransporter(cfg.Transport, id, cfg.Cluster.ID(), srv, srv.errorc, sstats, lstats)
	// add all remotes into transport
	for _, m := range remotes {
		if m.ID != id {
			tr.AddRemote(m.ID, m.PeerURLs)
		}
	}
	for _, m := range cfg.Cluster.Members() {
		if m.ID != id {
			tr.AddPeer(m.ID, m.PeerURLs)
		}
	}
	srv.r.transport = tr
	return srv, nil
}

// Start prepares and starts server in a new goroutine. It is no longer safe to
// modify a server's fields after it has been sent to Start.
// It also starts a goroutine to publish its server information.
func (s *EtcdServer) Start() {
	s.start()
	go s.publish(defaultPublishRetryInterval)
	go s.purgeFile()
	metrics.Publish("raft.status", expvar.Func(s.raftStatus))
}

// start prepares and starts server in a new goroutine. It is no longer safe to
// modify a server's fields after it has been sent to Start.
// This function is just used for testing.
func (s *EtcdServer) start() {
	if s.r.snapCount == 0 {
		log.Printf("etcdserver: set snapshot count to default %d", DefaultSnapCount)
		s.r.snapCount = DefaultSnapCount
	}
	s.w = wait.New()
	s.done = make(chan struct{})
	s.stop = make(chan struct{})
	// TODO: if this is an empty log, writes all peer infos
	// into the first entry
	go s.run()
}

func (s *EtcdServer) purgeFile() {
	var serrc, werrc <-chan error
	if s.cfg.MaxSnapFiles > 0 {
		serrc = fileutil.PurgeFile(s.cfg.SnapDir(), "snap", s.cfg.MaxSnapFiles, purgeFileInterval, s.done)
	}
	if s.cfg.MaxWALFiles > 0 {
		werrc = fileutil.PurgeFile(s.cfg.WALDir(), "wal", s.cfg.MaxWALFiles, purgeFileInterval, s.done)
	}
	select {
	case e := <-werrc:
		log.Fatalf("etcdserver: failed to purge wal file %v", e)
	case e := <-serrc:
		log.Fatalf("etcdserver: failed to purge snap file %v", e)
	case <-s.done:
		return
	}
}

func (s *EtcdServer) ID() types.ID { return s.id }

func (s *EtcdServer) RaftHandler() http.Handler { return s.r.transport.Handler() }

func (s *EtcdServer) Process(ctx context.Context, m raftpb.Message) error {
	if s.Cluster.IsIDRemoved(types.ID(m.From)) {
		log.Printf("etcdserver: reject message from removed member %s", types.ID(m.From).String())
		return httptypes.NewHTTPError(http.StatusForbidden, "cannot process message from removed member")
	}
	if m.Type == raftpb.MsgApp {
		s.stats.RecvAppendReq(types.ID(m.From).String(), m.Size())
	}
	return s.r.Step(ctx, m)
}

func (s *EtcdServer) run() {
	var syncC <-chan time.Time
	var shouldstop bool

	// load initial state from raft storage
	snap, err := s.r.raftStorage.Snapshot()
	if err != nil {
		log.Panicf("etcdserver: get snapshot from raft storage error: %v", err)
	}
	// snapi indicates the index of the last submitted snapshot request
	snapi := snap.Metadata.Index
	appliedi := snap.Metadata.Index
	confState := snap.Metadata.ConfState

	defer func() {
		s.r.Stop()
		s.r.transport.Stop()
		if err := s.r.storage.Close(); err != nil {
			log.Panicf("etcdserver: close storage error: %v", err)
		}
		close(s.done)
	}()
	// TODO: make raft loop a method on raftNode
	for {
		select {
		case <-s.r.ticker:
			s.r.Tick()
		case rd := <-s.r.Ready():
			if rd.SoftState != nil {
				atomic.StoreUint64(&s.r.lead, rd.SoftState.Lead)
				if rd.RaftState == raft.StateLeader {
					syncC = s.SyncTicker
					// TODO: remove the nil checking
					// current test utility does not provide the stats
					if s.stats != nil {
						s.stats.BecomeLeader()
					}
				} else {
					syncC = nil
				}
			}

			// apply snapshot to storage if it is more updated than current snapi
			if !raft.IsEmptySnap(rd.Snapshot) && rd.Snapshot.Metadata.Index > snapi {
				if err := s.r.storage.SaveSnap(rd.Snapshot); err != nil {
					log.Fatalf("etcdserver: save snapshot error: %v", err)
				}
				s.r.raftStorage.ApplySnapshot(rd.Snapshot)
				snapi = rd.Snapshot.Metadata.Index
				log.Printf("etcdserver: saved incoming snapshot at index %d", snapi)
			}

			if err := s.r.storage.Save(rd.HardState, rd.Entries); err != nil {
				log.Fatalf("etcdserver: save state and entries error: %v", err)
			}
			s.r.raftStorage.Append(rd.Entries)

			s.send(rd.Messages)

			// recover from snapshot if it is more updated than current applied
			if !raft.IsEmptySnap(rd.Snapshot) && rd.Snapshot.Metadata.Index > appliedi {
				if err := s.store.Recovery(rd.Snapshot.Data); err != nil {
					log.Panicf("recovery store error: %v", err)
				}
				s.Cluster.Recover()

				// recover raft transport
				s.r.transport.RemoveAllPeers()
				for _, m := range s.Cluster.Members() {
					if m.ID == s.ID() {
						continue
					}
					s.r.transport.AddPeer(m.ID, m.PeerURLs)
				}

				appliedi = rd.Snapshot.Metadata.Index
				confState = rd.Snapshot.Metadata.ConfState
				log.Printf("etcdserver: recovered from incoming snapshot at index %d", snapi)
			}
			// TODO(bmizerany): do this in the background, but take
			// care to apply entries in a single goroutine, and not
			// race them.
			if len(rd.CommittedEntries) != 0 {
				firsti := rd.CommittedEntries[0].Index
				if firsti > appliedi+1 {
					log.Panicf("etcdserver: first index of committed entry[%d] should <= appliedi[%d] + 1", firsti, appliedi)
				}
				var ents []raftpb.Entry
				if appliedi+1-firsti < uint64(len(rd.CommittedEntries)) {
					ents = rd.CommittedEntries[appliedi+1-firsti:]
				}
				if len(ents) > 0 {
					if appliedi, shouldstop = s.apply(ents, &confState); shouldstop {
						go s.stopWithDelay(10*100*time.Millisecond, fmt.Errorf("the member has been permanently removed from the cluster"))
					}
				}
			}

			s.r.Advance()

			if appliedi-snapi > s.r.snapCount {
				log.Printf("etcdserver: start to snapshot (applied: %d, lastsnap: %d)", appliedi, snapi)
				s.snapshot(appliedi, &confState)
				snapi = appliedi
			}
		case <-syncC:
			s.sync(defaultSyncTimeout)
		case err := <-s.errorc:
			log.Printf("etcdserver: %s", err)
			log.Printf("etcdserver: the data-dir used by this member must be removed.")
			return
		case <-s.stop:
			return
		}
	}
}

// Stop stops the server gracefully, and shuts down the running goroutine.
// Stop should be called after a Start(s), otherwise it will block forever.
func (s *EtcdServer) Stop() {
	select {
	case s.stop <- struct{}{}:
	case <-s.done:
		return
	}
	<-s.done
}

func (s *EtcdServer) stopWithDelay(d time.Duration, err error) {
	time.Sleep(d)
	select {
	case s.errorc <- err:
	default:
	}
}

// StopNotify returns a channel that receives a empty struct
// when the server is stopped.
func (s *EtcdServer) StopNotify() <-chan struct{} { return s.done }

// Do interprets r and performs an operation on s.store according to r.Method
// and other fields. If r.Method is "POST", "PUT", "DELETE", or a "GET" with
// Quorum == true, r will be sent through consensus before performing its
// respective operation. Do will block until an action is performed or there is
// an error.
func (s *EtcdServer) Do(ctx context.Context, r pb.Request) (Response, error) {
	r.ID = s.reqIDGen.Next()
	if r.Method == "GET" && r.Quorum {
		r.Method = "QGET"
	}
	switch r.Method {
	case "POST", "PUT", "DELETE", "QGET":
		data, err := r.Marshal()
		if err != nil {
			return Response{}, err
		}
		ch := s.w.Register(r.ID)
		s.r.Propose(ctx, data)
		select {
		case x := <-ch:
			resp := x.(Response)
			return resp, resp.err
		case <-ctx.Done():
			s.w.Trigger(r.ID, nil) // GC wait
			return Response{}, parseCtxErr(ctx.Err())
		case <-s.done:
			return Response{}, ErrStopped
		}
	case "GET":
		switch {
		case r.Wait:
			wc, err := s.store.Watch(r.Path, r.Recursive, r.Stream, r.Since)
			if err != nil {
				return Response{}, err
			}
			return Response{Watcher: wc}, nil
		default:
			ev, err := s.store.Get(r.Path, r.Recursive, r.Sorted)
			if err != nil {
				return Response{}, err
			}
			return Response{Event: ev}, nil
		}
	case "HEAD":
		ev, err := s.store.Get(r.Path, r.Recursive, r.Sorted)
		if err != nil {
			return Response{}, err
		}
		return Response{Event: ev}, nil
	default:
		return Response{}, ErrUnknownMethod
	}
}

func (s *EtcdServer) SelfStats() []byte { return s.stats.JSON() }

func (s *EtcdServer) LeaderStats() []byte {
	lead := atomic.LoadUint64(&s.r.lead)
	if lead != uint64(s.id) {
		return nil
	}
	return s.lstats.JSON()
}

func (s *EtcdServer) StoreStats() []byte { return s.store.JsonStats() }

func (s *EtcdServer) raftStatus() interface{} { return s.r.Status() }

func (s *EtcdServer) AddMember(ctx context.Context, memb Member) error {
	// TODO: move Member to protobuf type
	b, err := json.Marshal(memb)
	if err != nil {
		return err
	}
	cc := raftpb.ConfChange{
		Type:    raftpb.ConfChangeAddNode,
		NodeID:  uint64(memb.ID),
		Context: b,
	}
	return s.configure(ctx, cc)
}

func (s *EtcdServer) RemoveMember(ctx context.Context, id uint64) error {
	cc := raftpb.ConfChange{
		Type:   raftpb.ConfChangeRemoveNode,
		NodeID: id,
	}
	return s.configure(ctx, cc)
}

func (s *EtcdServer) UpdateMember(ctx context.Context, memb Member) error {
	b, err := json.Marshal(memb)
	if err != nil {
		return err
	}
	cc := raftpb.ConfChange{
		Type:    raftpb.ConfChangeUpdateNode,
		NodeID:  uint64(memb.ID),
		Context: b,
	}
	return s.configure(ctx, cc)
}

// Implement the RaftTimer interface
func (s *EtcdServer) Index() uint64 { return atomic.LoadUint64(&s.r.index) }

func (s *EtcdServer) Term() uint64 { return atomic.LoadUint64(&s.r.term) }

// Only for testing purpose
// TODO: add Raft server interface to expose raft related info:
// Index, Term, Lead, Committed, Applied, LastIndex, etc.
func (s *EtcdServer) Lead() uint64 { return atomic.LoadUint64(&s.r.lead) }

func (s *EtcdServer) Leader() types.ID { return types.ID(s.Lead()) }

// configure sends a configuration change through consensus and
// then waits for it to be applied to the server. It
// will block until the change is performed or there is an error.
func (s *EtcdServer) configure(ctx context.Context, cc raftpb.ConfChange) error {
	cc.ID = s.reqIDGen.Next()
	ch := s.w.Register(cc.ID)
	if err := s.r.ProposeConfChange(ctx, cc); err != nil {
		s.w.Trigger(cc.ID, nil)
		return err
	}
	select {
	case x := <-ch:
		if err, ok := x.(error); ok {
			return err
		}
		if x != nil {
			log.Panicf("return type should always be error")
		}
		return nil
	case <-ctx.Done():
		s.w.Trigger(cc.ID, nil) // GC wait
		return parseCtxErr(ctx.Err())
	case <-s.done:
		return ErrStopped
	}
}

// sync proposes a SYNC request and is non-blocking.
// This makes no guarantee that the request will be proposed or performed.
// The request will be cancelled after the given timeout.
func (s *EtcdServer) sync(timeout time.Duration) {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	req := pb.Request{
		Method: "SYNC",
		ID:     s.reqIDGen.Next(),
		Time:   time.Now().UnixNano(),
	}
	data := pbutil.MustMarshal(&req)
	// There is no promise that node has leader when do SYNC request,
	// so it uses goroutine to propose.
	go func() {
		s.r.Propose(ctx, data)
		cancel()
	}()
}

// publish registers server information into the cluster. The information
// is the JSON representation of this server's member struct, updated with the
// static clientURLs of the server.
// The function keeps attempting to register until it succeeds,
// or its server is stopped.
func (s *EtcdServer) publish(retryInterval time.Duration) {
	b, err := json.Marshal(s.attributes)
	if err != nil {
		log.Printf("etcdserver: json marshal error: %v", err)
		return
	}
	req := pb.Request{
		Method: "PUT",
		Path:   MemberAttributesStorePath(s.id),
		Val:    string(b),
	}

	for {
		ctx, cancel := context.WithTimeout(context.Background(), retryInterval)
		_, err := s.Do(ctx, req)
		cancel()
		switch err {
		case nil:
			log.Printf("etcdserver: published %+v to cluster %s", s.attributes, s.Cluster.ID())
			return
		case ErrStopped:
			log.Printf("etcdserver: aborting publish because server is stopped")
			return
		default:
			log.Printf("etcdserver: publish error: %v", err)
		}
	}
}

func (s *EtcdServer) send(ms []raftpb.Message) {
	for i, _ := range ms {
		if s.Cluster.IsIDRemoved(types.ID(ms[i].To)) {
			ms[i].To = 0
		}
	}
	s.r.transport.Send(ms)
}

// apply takes entries received from Raft (after it has been committed) and
// applies them to the current state of the EtcdServer.
// The given entries should not be empty.
func (s *EtcdServer) apply(es []raftpb.Entry, confState *raftpb.ConfState) (uint64, bool) {
	var applied uint64
	var shouldstop bool
	var err error
	for i := range es {
		e := es[i]
		switch e.Type {
		case raftpb.EntryNormal:
			var r pb.Request
			pbutil.MustUnmarshal(&r, e.Data)
			s.w.Trigger(r.ID, s.applyRequest(r))
		case raftpb.EntryConfChange:
			var cc raftpb.ConfChange
			pbutil.MustUnmarshal(&cc, e.Data)
			shouldstop, err = s.applyConfChange(cc, confState)
			s.w.Trigger(cc.ID, err)
		default:
			log.Panicf("entry type should be either EntryNormal or EntryConfChange")
		}
		atomic.StoreUint64(&s.r.index, e.Index)
		atomic.StoreUint64(&s.r.term, e.Term)
		applied = e.Index
	}
	return applied, shouldstop
}

// applyRequest interprets r as a call to store.X and returns a Response interpreted
// from store.Event
func (s *EtcdServer) applyRequest(r pb.Request) Response {
	f := func(ev *store.Event, err error) Response {
		return Response{Event: ev, err: err}
	}
	expr := timeutil.UnixNanoToTime(r.Expiration)
	switch r.Method {
	case "POST":
		return f(s.store.Create(r.Path, r.Dir, r.Val, true, expr))
	case "PUT":
		exists, existsSet := pbutil.GetBool(r.PrevExist)
		switch {
		case existsSet:
			if exists {
				if r.PrevIndex == 0 && r.PrevValue == "" {
					return f(s.store.Update(r.Path, r.Val, expr))
				} else {
					return f(s.store.CompareAndSwap(r.Path, r.PrevValue, r.PrevIndex, r.Val, expr))
				}
			}
			return f(s.store.Create(r.Path, r.Dir, r.Val, false, expr))
		case r.PrevIndex > 0 || r.PrevValue != "":
			return f(s.store.CompareAndSwap(r.Path, r.PrevValue, r.PrevIndex, r.Val, expr))
		default:
			if storeMemberAttributeRegexp.MatchString(r.Path) {
				id := mustParseMemberIDFromKey(path.Dir(r.Path))
				var attr Attributes
				if err := json.Unmarshal([]byte(r.Val), &attr); err != nil {
					log.Panicf("unmarshal %s should never fail: %v", r.Val, err)
				}
				s.Cluster.UpdateAttributes(id, attr)
			}
			return f(s.store.Set(r.Path, r.Dir, r.Val, expr))
		}
	case "DELETE":
		switch {
		case r.PrevIndex > 0 || r.PrevValue != "":
			return f(s.store.CompareAndDelete(r.Path, r.PrevValue, r.PrevIndex))
		default:
			return f(s.store.Delete(r.Path, r.Dir, r.Recursive))
		}
	case "QGET":
		return f(s.store.Get(r.Path, r.Recursive, r.Sorted))
	case "SYNC":
		s.store.DeleteExpiredKeys(time.Unix(0, r.Time))
		return Response{}
	default:
		// This should never be reached, but just in case:
		return Response{err: ErrUnknownMethod}
	}
}

// applyConfChange applies a ConfChange to the server. It is only
// invoked with a ConfChange that has already passed through Raft
func (s *EtcdServer) applyConfChange(cc raftpb.ConfChange, confState *raftpb.ConfState) (bool, error) {
	if err := s.Cluster.ValidateConfigurationChange(cc); err != nil {
		cc.NodeID = raft.None
		s.r.ApplyConfChange(cc)
		return false, err
	}
	*confState = *s.r.ApplyConfChange(cc)
	switch cc.Type {
	case raftpb.ConfChangeAddNode:
		m := new(Member)
		if err := json.Unmarshal(cc.Context, m); err != nil {
			log.Panicf("unmarshal member should never fail: %v", err)
		}
		if cc.NodeID != uint64(m.ID) {
			log.Panicf("nodeID should always be equal to member ID")
		}
		s.Cluster.AddMember(m)
		if m.ID == s.id {
			log.Printf("etcdserver: added local member %s %v to cluster %s", m.ID, m.PeerURLs, s.Cluster.ID())
		} else {
			s.r.transport.AddPeer(m.ID, m.PeerURLs)
			log.Printf("etcdserver: added member %s %v to cluster %s", m.ID, m.PeerURLs, s.Cluster.ID())
		}
	case raftpb.ConfChangeRemoveNode:
		id := types.ID(cc.NodeID)
		s.Cluster.RemoveMember(id)
		if id == s.id {
			return true, nil
		} else {
			s.r.transport.RemovePeer(id)
			log.Printf("etcdserver: removed member %s from cluster %s", id, s.Cluster.ID())
		}
	case raftpb.ConfChangeUpdateNode:
		m := new(Member)
		if err := json.Unmarshal(cc.Context, m); err != nil {
			log.Panicf("unmarshal member should never fail: %v", err)
		}
		if cc.NodeID != uint64(m.ID) {
			log.Panicf("nodeID should always be equal to member ID")
		}
		s.Cluster.UpdateRaftAttributes(m.ID, m.RaftAttributes)
		if m.ID == s.id {
			log.Printf("etcdserver: update local member %s %v in cluster %s", m.ID, m.PeerURLs, s.Cluster.ID())
		} else {
			s.r.transport.UpdatePeer(m.ID, m.PeerURLs)
			log.Printf("etcdserver: update member %s %v in cluster %s", m.ID, m.PeerURLs, s.Cluster.ID())
		}
	}
	return false, nil
}

// TODO: non-blocking snapshot
func (s *EtcdServer) snapshot(snapi uint64, confState *raftpb.ConfState) {
	d, err := s.store.Save()
	// TODO: current store will never fail to do a snapshot
	// what should we do if the store might fail?
	if err != nil {
		log.Panicf("etcdserver: store save should never fail: %v", err)
	}
	err = s.r.raftStorage.Compact(snapi, confState, d)
	if err != nil {
		// the snapshot was done asynchronously with the progress of raft.
		// raft might have already got a newer snapshot and called compact.
		if err == raft.ErrCompacted {
			return
		}
		log.Panicf("etcdserver: unexpected compaction error %v", err)
	}
	log.Printf("etcdserver: compacted log at index %d", snapi)

	if err := s.r.storage.Cut(); err != nil {
		log.Panicf("etcdserver: rotate wal file should never fail: %v", err)
	}
	snap, err := s.r.raftStorage.Snapshot()
	if err != nil {
		log.Panicf("etcdserver: snapshot error: %v", err)
	}
	if err := s.r.storage.SaveSnap(snap); err != nil {
		log.Fatalf("etcdserver: save snapshot error: %v", err)
	}
	log.Printf("etcdserver: saved snapshot at index %d", snap.Metadata.Index)
}

func (s *EtcdServer) PauseSending() { s.r.pauseSending() }

func (s *EtcdServer) ResumeSending() { s.r.resumeSending() }
