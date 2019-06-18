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

package migrate

import (
	"encoding/json"
	"errors"
	"fmt"
	"hash/crc32"
	"io/ioutil"
	"log"
	"net/url"
	"os"
	"path"
	"sort"
	"strconv"
	"strings"
	"time"

	raftpb "github.com/coreos/etcd/raft/raftpb"
)

type Snapshot4 struct {
	State     []byte `json:"state"`
	LastIndex uint64 `json:"lastIndex"`
	LastTerm  uint64 `json:"lastTerm"`

	Peers []struct {
		Name             string `json:"name"`
		ConnectionString string `json:"connectionString"`
	} `json:"peers"`
}

type Store4 struct {
	Root           *node
	CurrentIndex   uint64
	CurrentVersion int
}

type node struct {
	Path string

	CreatedIndex  uint64
	ModifiedIndex uint64

	Parent *node `json:"-"` // should not encode this field! avoid circular dependency.

	ExpireTime time.Time
	ACL        string
	Value      string           // for key-value pair
	Children   map[string]*node // for directory
}

func deepCopyNode(n *node, parent *node) *node {
	out := &node{
		Path:          n.Path,
		CreatedIndex:  n.CreatedIndex,
		ModifiedIndex: n.ModifiedIndex,
		Parent:        parent,
		ExpireTime:    n.ExpireTime,
		ACL:           n.ACL,
		Value:         n.Value,
		Children:      make(map[string]*node),
	}
	for k, v := range n.Children {
		out.Children[k] = deepCopyNode(v, out)
	}

	return out
}

func replacePathNames(n *node, s1, s2 string) {
	n.Path = path.Clean(strings.Replace(n.Path, s1, s2, 1))
	for _, c := range n.Children {
		replacePathNames(c, s1, s2)
	}
}

func pullNodesFromEtcd(n *node) map[string]uint64 {
	out := make(map[string]uint64)
	machines := n.Children["machines"]
	for name, c := range machines.Children {
		q, err := url.ParseQuery(c.Value)
		if err != nil {
			log.Fatal("Couldn't parse old query string value")
		}
		etcdurl := q.Get("etcd")
		rafturl := q.Get("raft")

		m := generateNodeMember(name, rafturl, etcdurl)
		out[m.Name] = uint64(m.ID)
	}
	return out
}

func fixEtcd(etcdref *node) *node {
	n := &node{
		Path:          "/0",
		CreatedIndex:  etcdref.CreatedIndex,
		ModifiedIndex: etcdref.ModifiedIndex,
		ExpireTime:    etcdref.ExpireTime,
		ACL:           etcdref.ACL,
		Children:      make(map[string]*node),
	}

	var machines *node
	if machineOrig, ok := etcdref.Children["machines"]; ok {
		machines = deepCopyNode(machineOrig, n)
	}
	if machines == nil {
		return n
	}
	n.Children["members"] = &node{
		Path:          "/0/members",
		CreatedIndex:  machines.CreatedIndex,
		ModifiedIndex: machines.ModifiedIndex,
		ExpireTime:    machines.ExpireTime,
		ACL:           machines.ACL,
		Children:      make(map[string]*node),
		Parent:        n,
	}
	for name, c := range machines.Children {
		q, err := url.ParseQuery(c.Value)
		if err != nil {
			log.Fatal("Couldn't parse old query string value")
		}
		etcdurl := q.Get("etcd")
		rafturl := q.Get("raft")

		m := generateNodeMember(name, rafturl, etcdurl)
		attrBytes, err := json.Marshal(m.attributes)
		if err != nil {
			log.Fatal("Couldn't marshal attributes")
		}
		raftBytes, err := json.Marshal(m.raftAttributes)
		if err != nil {
			log.Fatal("Couldn't marshal raft attributes")
		}
		newNode := &node{
			Path:          path.Join("/0/members", m.ID.String()),
			CreatedIndex:  c.CreatedIndex,
			ModifiedIndex: c.ModifiedIndex,
			ExpireTime:    c.ExpireTime,
			ACL:           c.ACL,
			Children:      make(map[string]*node),
			Parent:        n.Children["members"],
		}
		attrs := &node{
			Path:          path.Join("/0/members", m.ID.String(), "attributes"),
			CreatedIndex:  c.CreatedIndex,
			ModifiedIndex: c.ModifiedIndex,
			ExpireTime:    c.ExpireTime,
			ACL:           c.ACL,
			Value:         string(attrBytes),
			Parent:        newNode,
		}
		newNode.Children["attributes"] = attrs
		raftAttrs := &node{
			Path:          path.Join("/0/members", m.ID.String(), "raftAttributes"),
			CreatedIndex:  c.CreatedIndex,
			ModifiedIndex: c.ModifiedIndex,
			ExpireTime:    c.ExpireTime,
			ACL:           c.ACL,
			Value:         string(raftBytes),
			Parent:        newNode,
		}
		newNode.Children["raftAttributes"] = raftAttrs
		n.Children["members"].Children[m.ID.String()] = newNode
	}
	return n
}

func mangleRoot(n *node) *node {
	newRoot := &node{
		Path:          "/",
		CreatedIndex:  n.CreatedIndex,
		ModifiedIndex: n.ModifiedIndex,
		ExpireTime:    n.ExpireTime,
		ACL:           n.ACL,
		Children:      make(map[string]*node),
	}
	newRoot.Children["1"] = n
	etcd := n.Children["_etcd"]
	replacePathNames(n, "/", "/1/")
	newZero := fixEtcd(etcd)
	newZero.Parent = newRoot
	newRoot.Children["0"] = newZero
	return newRoot
}

func (s *Snapshot4) GetNodesFromStore() map[string]uint64 {
	st := &Store4{}
	if err := json.Unmarshal(s.State, st); err != nil {
		log.Fatal("Couldn't unmarshal snapshot")
	}
	etcd := st.Root.Children["_etcd"]
	return pullNodesFromEtcd(etcd)
}

func (s *Snapshot4) Snapshot2() *raftpb.Snapshot {
	st := &Store4{}
	if err := json.Unmarshal(s.State, st); err != nil {
		log.Fatal("Couldn't unmarshal snapshot")
	}
	st.Root = mangleRoot(st.Root)

	newState, err := json.Marshal(st)
	if err != nil {
		log.Fatal("Couldn't re-marshal new snapshot")
	}

	nodes := s.GetNodesFromStore()
	nodeList := make([]uint64, 0)
	for _, v := range nodes {
		nodeList = append(nodeList, v)
	}

	snap2 := raftpb.Snapshot{
		Data: newState,
		Metadata: raftpb.SnapshotMetadata{
			Index: s.LastIndex,
			Term:  s.LastTerm + termOffset4to2,
			ConfState: raftpb.ConfState{
				Nodes: nodeList,
			},
		},
	}

	return &snap2
}

func DecodeLatestSnapshot4FromDir(snapdir string) (*Snapshot4, error) {
	fname, err := FindLatestFile(snapdir)
	if err != nil {
		return nil, err
	}

	if fname == "" {
		return nil, nil
	}

	snappath := path.Join(snapdir, fname)
	log.Printf("Decoding snapshot from %s", snappath)

	return DecodeSnapshot4FromFile(snappath)
}

// FindLatestFile identifies the "latest" filename in a given directory
// by sorting all the files and choosing the highest value.
func FindLatestFile(dirpath string) (string, error) {
	dir, err := os.OpenFile(dirpath, os.O_RDONLY, 0)
	if err != nil {
		if os.IsNotExist(err) {
			err = nil
		}
		return "", err
	}
	defer dir.Close()

	fnames, err := dir.Readdirnames(-1)
	if err != nil {
		return "", err
	}

	if len(fnames) == 0 {
		return "", nil
	}

	names, err := NewSnapshotFileNames(fnames)
	if err != nil {
		return "", err
	}

	return names[len(names)-1].FileName, nil
}

func DecodeSnapshot4FromFile(path string) (*Snapshot4, error) {
	// Read snapshot data.
	f, err := os.OpenFile(path, os.O_RDONLY, 0)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	return DecodeSnapshot4(f)
}

func DecodeSnapshot4(f *os.File) (*Snapshot4, error) {
	// Verify checksum
	var checksum uint32
	n, err := fmt.Fscanf(f, "%08x\n", &checksum)
	if err != nil {
		return nil, err
	} else if n != 1 {
		return nil, errors.New("miss heading checksum")
	}

	// Load remaining snapshot contents.
	b, err := ioutil.ReadAll(f)
	if err != nil {
		return nil, err
	}

	// Generate checksum.
	byteChecksum := crc32.ChecksumIEEE(b)
	if uint32(checksum) != byteChecksum {
		return nil, errors.New("bad checksum")
	}

	// Decode snapshot.
	snapshot := new(Snapshot4)
	if err = json.Unmarshal(b, snapshot); err != nil {
		return nil, err
	}
	return snapshot, nil
}

func NewSnapshotFileNames(names []string) ([]SnapshotFileName, error) {

	s := make([]SnapshotFileName, 0)
	for _, n := range names {
		trimmed := strings.TrimSuffix(n, ".ss")
		if trimmed == n {
			return nil, fmt.Errorf("file %q does not have .ss extension", n)
		}

		parts := strings.SplitN(trimmed, "_", 2)
		if len(parts) != 2 {
			return nil, fmt.Errorf("unrecognized file name format %q", n)
		}

		fn := SnapshotFileName{FileName: n}

		var err error
		fn.Term, err = strconv.ParseUint(parts[0], 10, 64)
		if err != nil {
			return nil, fmt.Errorf("unable to parse term from filename %q: %v", n, err)
		}

		fn.Index, err = strconv.ParseUint(parts[1], 10, 64)
		if err != nil {
			return nil, fmt.Errorf("unable to parse index from filename %q: %v", n, err)
		}

		s = append(s, fn)
	}

	sortable := SnapshotFileNames(s)
	sort.Sort(&sortable)
	return s, nil
}

type SnapshotFileNames []SnapshotFileName
type SnapshotFileName struct {
	FileName string
	Term     uint64
	Index    uint64
}

func (n *SnapshotFileNames) Less(i, j int) bool {
	iTerm, iIndex := (*n)[i].Term, (*n)[i].Index
	jTerm, jIndex := (*n)[j].Term, (*n)[j].Index
	return iTerm < jTerm || (iTerm == jTerm && iIndex < jIndex)
}

func (n *SnapshotFileNames) Swap(i, j int) {
	(*n)[i], (*n)[j] = (*n)[j], (*n)[i]
}

func (n *SnapshotFileNames) Len() int {
	return len([]SnapshotFileName(*n))
}
