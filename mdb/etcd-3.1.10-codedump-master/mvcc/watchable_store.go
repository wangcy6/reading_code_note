// Copyright 2015 The etcd Authors
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

package mvcc

import (
	"sync"
	"time"

	"github.com/coreos/etcd/lease"
	"github.com/coreos/etcd/mvcc/backend"
	"github.com/coreos/etcd/mvcc/mvccpb"
)

const (
	// chanBufLen is the length of the buffered chan
	// for sending out watched events.
	// TODO: find a good buf value. 1024 is just a random one that
	// seems to be reasonable.
	chanBufLen = 1024

	// maxWatchersPerSync is the number of watchers to sync in a single batch
	maxWatchersPerSync = 512
)

type watchable interface {
	watch(key, end []byte, startRev int64, id WatchID, ch chan<- WatchResponse, fcs ...FilterFunc) (*watcher, cancelFunc)
	progress(w *watcher)
	rev() int64
}

type watchableStore struct {
	mu sync.Mutex

	*store

	// victims are watcher batches that were blocked on the watch channel
	victims []watcherBatch
	victimc chan struct{}

	// contains all unsynced watchers that needs to sync with events that have happened
	// 未同步的watcher集合
	unsynced watcherGroup

	// contains all synced watchers that are in sync with the progress of the store.
	// The key of the map is the key that the watcher watches on.
	// 已经同步的watcher集合
	synced watcherGroup

	stopc chan struct{}
	wg    sync.WaitGroup
}

// cancelFunc updates unsynced and synced maps when running
// cancel operations.
type cancelFunc func()

func New(b backend.Backend, le lease.Lessor, ig ConsistentIndexGetter) ConsistentWatchableKV {
	return newWatchableStore(b, le, ig)
}

func newWatchableStore(b backend.Backend, le lease.Lessor, ig ConsistentIndexGetter) *watchableStore {
	s := &watchableStore{
		store:    NewStore(b, le, ig),
		victimc:  make(chan struct{}, 1),
		unsynced: newWatcherGroup(),
		synced:   newWatcherGroup(),
		stopc:    make(chan struct{}),
	}
	if s.le != nil {
		// use this store as the deleter so revokes trigger watch events
		s.le.SetRangeDeleter(s)
	}
	s.wg.Add(2)
	go s.syncWatchersLoop()
	go s.syncVictimsLoop()
	return s
}

func (s *watchableStore) Put(key, value []byte, lease lease.LeaseID) (rev int64) {
	s.mu.Lock()
	defer s.mu.Unlock()

	// PUT数据进去
	rev = s.store.Put(key, value, lease)
	// 拿到有哪些变化
	changes := s.store.getChanges()
	if len(changes) != 1 {
		plog.Panicf("unexpected len(changes) != 1 after put")
	}

	ev := mvccpb.Event{
		Type: mvccpb.PUT,
		Kv:   &changes[0],
	}
	// 通过notify通知对这些变化感兴趣的watcher
	s.notify(rev, []mvccpb.Event{ev})
	return rev
}

func (s *watchableStore) DeleteRange(key, end []byte) (n, rev int64) {
	s.mu.Lock()
	defer s.mu.Unlock()

	n, rev = s.store.DeleteRange(key, end)
	changes := s.store.getChanges()

	if len(changes) != int(n) {
		plog.Panicf("unexpected len(changes) != n after deleteRange")
	}

	if n == 0 {
		return n, rev
	}

	evs := make([]mvccpb.Event, n)
	for i := range changes {
		evs[i] = mvccpb.Event{
			Type: mvccpb.DELETE,
			Kv:   &changes[i]}
		evs[i].Kv.ModRevision = rev
	}
	s.notify(rev, evs)
	return n, rev
}

func (s *watchableStore) TxnBegin() int64 {
	s.mu.Lock()
	return s.store.TxnBegin()
}

func (s *watchableStore) TxnEnd(txnID int64) error {
	err := s.store.TxnEnd(txnID)
	if err != nil {
		return err
	}

	changes := s.getChanges()
	if len(changes) == 0 {
		s.mu.Unlock()
		return nil
	}

	rev := s.store.Rev()
	evs := make([]mvccpb.Event, len(changes))
	for i, change := range changes {
		switch change.CreateRevision {
		case 0:
			evs[i] = mvccpb.Event{
				Type: mvccpb.DELETE,
				Kv:   &changes[i]}
			evs[i].Kv.ModRevision = rev
		default:
			evs[i] = mvccpb.Event{
				Type: mvccpb.PUT,
				Kv:   &changes[i]}
		}
	}

	s.notify(rev, evs)
	s.mu.Unlock()

	return nil
}

func (s *watchableStore) Close() error {
	close(s.stopc)
	s.wg.Wait()
	return s.store.Close()
}

func (s *watchableStore) NewWatchStream() WatchStream {
	watchStreamGauge.Inc()
	return &watchStream{
		watchable: s,
		ch:        make(chan WatchResponse, chanBufLen),
		cancels:   make(map[WatchID]cancelFunc),
		watchers:  make(map[WatchID]*watcher),
	}
}

// 创建都某个key范围感兴趣的watcher返回
func (s *watchableStore) watch(key, end []byte, startRev int64, id WatchID, ch chan<- WatchResponse, fcs ...FilterFunc) (*watcher, cancelFunc) {
	s.mu.Lock()
	defer s.mu.Unlock()

	wa := &watcher{
		key:    key,
		end:    end,
		minRev: startRev,
		id:     id,
		ch:     ch,
		fcs:    fcs,
	}

	s.store.mu.Lock()
	// 如果该watcher要求的起始revision大于当前的revision了，或者startRev为0，说明这些数据已经同步过了
	synced := startRev > s.store.currentRev.main || startRev == 0
	if synced {
		// 已经同步过的watcher，修改其minRev
		wa.minRev = s.store.currentRev.main + 1
		if startRev > wa.minRev {
			wa.minRev = startRev
		}
	}
	s.store.mu.Unlock()
	// 具体放到哪个wg中
	if synced {
		s.synced.add(wa)
	} else {
		slowWatcherGauge.Inc()
		s.unsynced.add(wa)
	}
	watcherGauge.Inc()

	return wa, func() { s.cancelWatcher(wa) }
}

// cancelWatcher removes references of the watcher from the watchableStore
// 停止一个watcher
func (s *watchableStore) cancelWatcher(wa *watcher) {
	// 注意这里是一个循环
	for {
		s.mu.Lock()

		if s.unsynced.delete(wa) {
			slowWatcherGauge.Dec()
			break
		} else if s.synced.delete(wa) {
			break
		} else if wa.compacted {
			break
		}

		// 如果没有这个标记位，说明不对直接panic
		if !wa.victim {
			panic("watcher not victim but not in watch groups")
		}

		var victimBatch watcherBatch
		for _, wb := range s.victims {
			if wb[wa] != nil {
				victimBatch = wb
				break
			}
		}
		if victimBatch != nil {
			slowWatcherGauge.Dec()
			delete(victimBatch, wa)
			break
		}

		// victim being processed so not accessible; retry
		s.mu.Unlock()
		// sleep一下等待下一次操作
		time.Sleep(time.Millisecond)
	}

	watcherGauge.Dec()
	s.mu.Unlock()
}

// syncWatchersLoop syncs the watcher in the unsynced map every 100ms.
func (s *watchableStore) syncWatchersLoop() {
	defer s.wg.Done()

	for {
		s.mu.Lock()
		st := time.Now()
		// 拿到当前未同步wg的数量
		lastUnsyncedWatchers := s.unsynced.size()
		// 进行同步操作
		s.syncWatchers()
		// 拿到同步之后未同步wg的数量
		unsyncedWatchers := s.unsynced.size()
		s.mu.Unlock()
		// 计算中间消耗了多少时间
		syncDuration := time.Since(st)

		waitDuration := 100 * time.Millisecond
		// more work pending?
		// 还有未同步的worker，并且做完同步操作之后，未同步worker数量减少了
		if unsyncedWatchers != 0 && lastUnsyncedWatchers > unsyncedWatchers {
			// be fair to other store operations by yielding time taken
			waitDuration = syncDuration
		}

		select {
		case <-time.After(waitDuration):
		case <-s.stopc:
			return
		}
	}
}

// syncVictimsLoop tries to write precomputed watcher responses to
// watchers that had a blocked watcher channel
func (s *watchableStore) syncVictimsLoop() {
	defer s.wg.Done()

	for {
		//
		for s.moveVictims() != 0 {
			// try to update all victim watchers
		}
		s.mu.Lock()
		// victims数组是否为空？
		isEmpty := len(s.victims) == 0
		s.mu.Unlock()

		var tickc <-chan time.Time
		if !isEmpty {
			// 如果不为空，最多等待10毫秒继续下一次操作
			tickc = time.After(10 * time.Millisecond)
		}

		select {
		case <-tickc:
		case <-s.victimc:
		case <-s.stopc:
			return
		}
	}
}

// moveVictims tries to update watches with already pending event data
func (s *watchableStore) moveVictims() (moved int) {
	s.mu.Lock()
	// 将当前的victims存放到一个临时对象，原有的清空
	victims := s.victims
	s.victims = nil
	s.mu.Unlock()

	// 新的存放在这里
	var newVictim watcherBatch
	// 遍历当前的victims
	for _, wb := range victims {
		// try to send responses again
		for w, eb := range wb {
			// watcher has observed the store up to, but not including, w.minRev
			rev := w.minRev - 1
			if w.send(WatchResponse{WatchID: w.id, Events: eb.evs, Revision: rev}) {
				pendingEventsGauge.Add(float64(len(eb.evs)))
			} else {
				// 发送失败，继续添加回newVictim中
				if newVictim == nil {
					newVictim = make(watcherBatch)
				}
				newVictim[w] = eb
				continue
			}
			moved++
		}

		// assign completed victim watchers to unsync/sync
		s.mu.Lock()
		s.store.mu.Lock()
		// 当前revision
		curRev := s.store.currentRev.main
		// 遍历watcherBatch
		for w, eb := range wb {
			// 如果在newVictim中已经存在，就忽略
			if newVictim != nil && newVictim[w] != nil {
				// couldn't send watch response; stays victim
				continue
			}
			w.victim = false
			if eb.moreRev != 0 {
				w.minRev = eb.moreRev
			}
			if w.minRev <= curRev {
				// 如果小于当前revision，就继续添加回未同步wg中，等待下一次同步
				s.unsynced.add(w)
			} else {
				// 否则同步完成了
				slowWatcherGauge.Dec()
				s.synced.add(w)
			}
		}
		s.store.mu.Unlock()
		s.mu.Unlock()
	}

	if len(newVictim) > 0 {
		s.mu.Lock()
		// 添加到victims中
		s.victims = append(s.victims, newVictim)
		s.mu.Unlock()
	}

	// 返回这一次操作了多少wg
	return moved
}

// syncWatchers syncs unsynced watchers by:
//	1. choose a set of watchers from the unsynced watcher group
//	2. iterate over the set to get the minimum revision and remove compacted watchers
//	3. use minimum revision to get all key-value pairs and send those events to watchers
//	4. remove synced watchers in set from unsynced group and move to synced group
// 同步watcher
func (s *watchableStore) syncWatchers() {
	// 当前未同步数量为0，直接返回
	if s.unsynced.size() == 0 {
		return
	}

	s.store.mu.Lock()
	defer s.store.mu.Unlock()

	// in order to find key-value pairs from unsynced watchers, we need to
	// find min revision index, and these revisions can be used to
	// query the backend store of key-value pairs
	// 拿到当前存储的revision
	curRev := s.store.currentRev.main
	// 拿到compact revision
	compactionRev := s.store.compactMainRev
	// 选择需要进行同步的wg，以及最小revision
	wg, minRev := s.unsynced.choose(maxWatchersPerSync, curRev, compactionRev)
	minBytes, maxBytes := newRevBytes(), newRevBytes()
	revToBytes(revision{main: minRev}, minBytes)
	revToBytes(revision{main: curRev + 1}, maxBytes)

	// UnsafeRange returns keys and values. And in boltdb, keys are revisions.
	// values are actual key-value pairs in backend.
	tx := s.store.b.BatchTx()
	tx.Lock()
	// 拿到revision以及value，因为在etcd中，存放在boltdb中的key是revision
	revs, vs := tx.UnsafeRange(keyBucketName, minBytes, maxBytes, 0)
	// 转换成event
	evs := kvsToEvents(wg, revs, vs)
	tx.Unlock()

	var victims watcherBatch
	wb := newWatcherBatch(wg, evs)
	for w := range wg.watchers {
		w.minRev = curRev + 1

		eb, ok := wb[w]
		if !ok {
			// bring un-notified watcher to synced
			s.synced.add(w)
			s.unsynced.delete(w)
			continue
		}

		if eb.moreRev != 0 {
			w.minRev = eb.moreRev
		}

		if w.send(WatchResponse{WatchID: w.id, Events: eb.evs, Revision: curRev}) {
			pendingEventsGauge.Add(float64(len(eb.evs)))
		} else {
			if victims == nil {
				victims = make(watcherBatch)
			}
			w.victim = true
		}

		if w.victim {
			victims[w] = eb
		} else {
			if eb.moreRev != 0 {
				// stay unsynced; more to read
				continue
			}
			s.synced.add(w)
		}
		s.unsynced.delete(w)
	}
	s.addVictim(victims)

	vsz := 0
	for _, v := range s.victims {
		vsz += len(v)
	}
	slowWatcherGauge.Set(float64(s.unsynced.size() + vsz))
}

// kvsToEvents gets all events for the watchers from all key-value pairs
// 返回所有包含传入的kv数据的事件
func kvsToEvents(wg *watcherGroup, revs, vals [][]byte) (evs []mvccpb.Event) {
	// 因为在etcd中，key是revision，而value中才包含key，所以这里是遍历value来查key的
	for i, v := range vals {
		var kv mvccpb.KeyValue
		if err := kv.Unmarshal(v); err != nil {
			plog.Panicf("cannot unmarshal event: %v", err)
		}

		// 不包含这个key，忽略
		if !wg.contains(string(kv.Key)) {
			continue
		}

		ty := mvccpb.PUT
		// 如果revision过期了？
		if isTombstone(revs[i]) {
			// 删除
			ty = mvccpb.DELETE
			// patch in mod revision so watchers won't skip
			kv.ModRevision = bytesToRev(revs[i]).main
		}
		// 添加到event数组中
		evs = append(evs, mvccpb.Event{Kv: &kv, Type: ty})
	}
	return evs
}

// notify notifies the fact that given event at the given rev just happened to
// watchers that watch on the key of the event.
func (s *watchableStore) notify(rev int64, evs []mvccpb.Event) {
	var victim watcherBatch
	// 根据这些事件，从sync的wg中返回watcherBatch
	for w, eb := range newWatcherBatch(&s.synced, evs) {
		if eb.revs != 1 {
			plog.Panicf("unexpected multiple revisions in notification")
		}

		// 发送WatcherResponse
		if w.send(WatchResponse{WatchID: w.id, Events: eb.evs, Revision: rev}) {
			// 发送成功
			pendingEventsGauge.Add(float64(len(eb.evs)))
		} else {
			// 发送失败
			// move slow watcher to victims
			w.minRev = rev + 1
			if victim == nil {
				victim = make(watcherBatch)
			}
			// 添加到victim中
			w.victim = true
			victim[w] = eb
			// 从synced中删除
			s.synced.delete(w)
			slowWatcherGauge.Inc()
		}
	}
	s.addVictim(victim)
}

func (s *watchableStore) addVictim(victim watcherBatch) {
	if victim == nil {
		return
	}
	s.victims = append(s.victims, victim)
	select {
	case s.victimc <- struct{}{}:
	default:
	}
}

func (s *watchableStore) rev() int64 { return s.store.Rev() }

func (s *watchableStore) progress(w *watcher) {
	s.mu.Lock()
	defer s.mu.Unlock()

	if _, ok := s.synced.watchers[w]; ok {
		w.send(WatchResponse{WatchID: w.id, Revision: s.rev()})
		// If the ch is full, this watcher is receiving events.
		// We do not need to send progress at all.
	}
}

type watcher struct {
	// the watcher key
	// watcher关注的key
	key []byte
	// end indicates the end of the range to watch.
	// If end is set, the watcher is on a range.
	// 如果end不为nil，说明watcher关注的是范围key
	end []byte

	// victim is set when ch is blocked and undergoing victim processing
	victim bool

	// compacted is set when the watcher is removed because of compaction
	compacted bool

	// minRev is the minimum revision update the watcher will accept
	// 关注的最小revision，只有大于这个revision的更新才通知watcher
	minRev int64

	// watcher ID
	id     WatchID

	// 过滤函数数组
	fcs []FilterFunc
	// a chan to send out the watch response.
	// The chan might be shared with other watchers.
	ch chan<- WatchResponse
}

func (w *watcher) send(wr WatchResponse) bool {
	progressEvent := len(wr.Events) == 0

	// 如果有FilterFunc，就进行过滤处理
	if len(w.fcs) != 0 {
		ne := make([]mvccpb.Event, 0, len(wr.Events))
		for i := range wr.Events {
			filtered := false
			for _, filter := range w.fcs {
				if filter(wr.Events[i]) {
					filtered = true
					break
				}
			}
			// 没有被过滤就添加进来
			if !filtered {
				ne = append(ne, wr.Events[i])
			}
		}
		// 保存过滤之后的事件
		wr.Events = ne
	}

	// if all events are filtered out, we should send nothing.
	// 如果之前有事件，但是被过滤完毕了，就直接返回
	if !progressEvent && len(wr.Events) == 0 {
		return true
	}
	// 通过channel发送response
	select {
	case w.ch <- wr:
		return true
	default:
		return false
	}
}