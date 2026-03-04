# LIVE SYSTEM VERIFICATION — TradeLearn 1v1 Multiplayer

**Date:** Fresh code-level trace of every method call chain  
**Method:** Read every source file, traced actual call paths, validated transactional boundaries  
**Trust level:** ZERO — no prior assumptions carried forward  

---

## FILES TRACED (complete reads)

| File | Lines | Role |
|------|-------|------|
| MatchService.java | 696 | Match lifecycle: create, join, rematch, end, abandon |
| MatchmakingService.java | 367 | Instant event-driven matchmaking engine |
| RoomManager.java | 471 | Hybrid local+Redis room state manager |
| MatchSchedulerService.java | 586 | Per-game candle tick scheduler |
| MatchTradeService.java | 340 | Trade execution + position tracking |
| PositionSnapshotStore.java | 350 | In-memory O(1) position snapshots |
| WebSocketEventListener.java | ~250 | Connect/disconnect + grace period |
| GameWebSocketHandler.java | ~500 | STOMP message handlers |
| TradeProcessingPipeline.java | ~280 | Async trade pipeline + backpressure |
| TradeRateLimiter.java | 65 | Bucket4j per-player rate limiter |
| ResilientRedisRoomStore.java | 520 | Circuit-breaking Redis wrapper |
| RedisRoomStore.java | 410 | Lua-scripted Redis room state |
| GameBroadcaster.java | ~110 | Local + Redis relay WS delivery |
| CandleService.java | ~210 | Server-authoritative candle progression |
| EloUtil.java | 56 | Standard ELO calculator (K=32) |
| ScoringUtil.java | 90 | Hybrid scoring (60/20/20) |
| GameRepository.java | 62 | Pessimistic lock + CAS atomic join |
| Game.java | ~180 | Entity with @Version optimistic lock |
| useGameSocket.js | 425 | Frontend STOMP hook (12 subscriptions) |

---

## SUBSYSTEM-BY-SUBSYSTEM VERIFICATION

---

### 1. MATCHMAKING

**Call chain:** `MatchmakingService.enqueue()` → `ticketIndex.putIfAbsent()` → `skipList.add()` → `tryInstantMatch()` → `pickBestMatch()` → `tryPair()`

**tryPair internals (MatchmakingService.java ~L260-L300):**
```
RLock lock = redisson.getLock("matchmaking:pair:" + lo + ":" + hi)
lock.tryLock(500ms, 3s) → ticketIndex.remove(lo) → ticketIndex.remove(hi) → skipList.remove both → lock.unlock()
matchService.createAutoMatch(lo, hi)  ← OUTSIDE lock
```

**What I verified:**
- ConcurrentSkipListSet provides O(log n) nearest-neighbor lookup via `lower()`/`higher()`
- ConcurrentHashMap.putIfAbsent prevents duplicate enqueue (idempotent)
- Redisson distributed lock prevents two instances from pairing the same duo
- Match creation (DB transaction) happens OUTSIDE the lock — good, no DB inside critical section
- If `createAutoMatch` fails, both players are silently removed from the queue — **no auto-recovery**

**Critical finding — JVM-local queue:**  
`skipList` and `ticketIndex` are instance-local `ConcurrentSkipListSet`/`ConcurrentHashMap`. In a multi-instance deployment, players enqueued on different instances **will never see each other**. The Redisson lock is only useful if both players happen to enqueue on the same instance.

**Verdict:** ✅ SAFE (single instance) / ❌ BROKEN (multi-instance matchmaking is disjoint)

---

### 2. GAME CREATION

**Call chain (manual):** `MatchService.createMatch()` → `gameRepository.save(WAITING)` → `roomManager.createRoom()` — **INSIDE @Transactional**

**Call chain (auto):** `MatchService.createAutoMatch()` → `gameRepository.save(ACTIVE)` → `afterCommit { roomManager.createRoom() + joinRoom() + loadCandles() + startProgression() + initializePosition() }`

**What I verified:**
- `createMatch()`: `roomManager.createRoom()` is called **inside** the @Transactional boundary (MatchService.java ~L42-L52). If the DB commit fails (e.g., constraint violation), the Redis room is already created → **orphaned Redis room**.
- `createAutoMatch()`: All side effects in afterCommit callback ✅
- `requestRematch()` (ACCEPTED path): All side effects in afterCommit callback ✅
- RedisRoomStore.createRoom uses Lua script with EXISTS check → idempotent (SETNX semantics)

**Verdict:** ⚠ RISK — `createMatch()` has Redis side effect inside TX boundary, inconsistent with the afterCommit pattern used everywhere else.

---

### 3. JOIN FLOW

**Call chain:** `MatchService.joinMatch()` → room capacity check → `gameRepository.atomicJoin()` (CAS) → `findByIdForUpdate()` (pessimistic) → `afterCommit { joinRoom + loadCandles + startProgression + initializePosition + broadcast }`

**atomicJoin SQL (GameRepository.java ~L45-L55):**
```sql
UPDATE Game SET status='ACTIVE', opponent=:opponent, startTime=NOW(),
               version=version+1
WHERE id=:gameId AND status='WAITING'
```
Returns 0 if already taken. `clearAutomatically=true` evicts L1 cache.

**What I verified:**
- CAS ensures exactly 1 opponent joins a WAITING game — concurrent joiners get `updated==0`
- `findByIdForUpdate` after CAS provides a pessimistic lock for validation (e.g., can't join own game)
- If validation fails (same user), RuntimeException rolls back the entire TX including the CAS UPDATE
- All side effects (Redis room, candles, scheduler, positions, WS broadcast) are in `afterCommit` ✅
- If afterCommit fails, DB is committed ACTIVE but no Redis room exists — logged at ERROR level, mentions "needs reconciliation" but **no automated reconciliation exists**

**Verdict:** ✅ SAFE — DB integrity guaranteed. afterCommit failure is a latent orphan risk but non-corruptive.

---

### 4. REDIS ROOMS

**Architecture:** RoomManager → ResilientRedisRoomStore → RedisRoomStore

**Key schema (RedisRoomStore.java):**
```
tl:room:{gameId}              → Hash  {creatorId, phase, createdAt}
tl:room:{gameId}:players      → Set   {userId …}
tl:room:{gameId}:disconnected → Set   {userId …}
tl:room:{gameId}:ready        → String (atomic counter)
tl:room:index                 → Set   {gameId …}
tl:sched:owner:{gameId}       → String (instanceId) with TTL
```

**What I verified:**
- `createRoom`: Lua script — EXISTS check + HSET + SADD + EXPIRE → atomic, idempotent
- `joinRoom`: Lua script — EXISTS + SISMEMBER (prevent double-join) + SCARD (capacity) + SADD + phase transition → atomic
- `readyUp`: Lua script — INCR + reset at threshold → atomic
- `claimScheduler`: Lua script — GET + SET-NX-with-TTL + refresh-if-owner → atomic
- All rooms have 2-hour safety-net TTL (auto-expire if cleanup fails)
- Circuit breaker: 5 consecutive failures → 30s cooldown → shadow cache fallback

**endGame cleanup (RoomManager.java ~L175-L210):**
```
setPhase(terminal) → cancel local scheduler → releaseScheduler (Redis) 
→ cleanupLocalSessions → cancelAllReconnectTimers → deleteRoom (all 5 keys + index)
```
Idempotent — safe from multiple calls.

**Verdict:** ✅ SAFE

---

### 5. SCHEDULER OWNERSHIP

**Call chain:** `MatchSchedulerService.startProgression()` → `roomManager.tryClaimScheduler()` → Redis SETNX → `runningTasks.computeIfAbsent()` → `taskScheduler.scheduleAtFixedRate(tick, 5s)`

**Tick ownership refresh:** Each `tick()` call → `roomManager.refreshSchedulerOwnership()` → Redis EXPIRE refresh (2-min TTL)

**What I verified:**
- Only the instance that wins SETNX runs the scheduler (startProgression returns false for others)
- `computeIfAbsent` prevents duplicate schedulers on the same instance
- TTL refresh on every 5s tick keeps ownership alive indefinitely during normal operation
- On `stopProgression`: cancels ScheduledFuture + releases Redis ownership key

**Crash recovery gap:**  
If the owning instance crashes:
1. Redis SETNX key expires after 2 minutes (TTL)
2. The game remains ACTIVE in the DB
3. **No other instance will automatically start a scheduler** for the orphaned game
4. Candles stop progressing forever
5. No reconciliation or recovery job exists to detect this

**Verdict:** ⚠ RISK — No crash recovery for scheduler ownership. Orphaned ACTIVE games stall.

---

### 6. CANDLE PROGRESSION

**Tick call chain:** `tick(gameId)` → freeze check → `refreshSchedulerOwnership()` → status check → `candleService.advanceCandle()` (@Transactional + findByIdForUpdate) → broadcast candle + scoreboard

**advanceCandle (CandleService.java ~L170-L185):**
```java
@Transactional
public Candle advanceCandle(long gameId) {
    Game game = gameRepository.findByIdForUpdate(gameId);  // PESSIMISTIC_WRITE
    int nextIndex = game.getCurrentCandleIndex() + 1;
    if (nextIndex >= candles.size()) return null;
    game.setCurrentCandleIndex(nextIndex);
    gameRepository.save(game);
    return candles.get(nextIndex);
}
```

**What I verified:**
- `tick()` itself is NOT @Transactional (called from scheduler thread)
- `advanceCandle()` opens its own TX with pessimistic lock → overlapping ticks are serialized
- If overlapping tick arrives, it blocks on SELECT FOR UPDATE → reads already-incremented index → advances correctly (no candle skip, no double-advance)
- When candles exhausted (advanceCandle returns null): `autoFinishGame()` + `stopProgression()`
- First candle is broadcast immediately in `startProgression()` via `broadcastCurrentCandle()`

**Verdict:** ✅ SAFE

---

### 7. TRADE EXECUTION

**Call chain:** WS message → `GameWebSocketHandler.handleTrade()` → validate payload → rate limiter → `tradePipeline.submitTrade()` → `matchTradeService.placeTrade()` (@Transactional + findByIdForUpdate)

**placeTrade internals (MatchTradeService.java ~L55-L145):**
```
findByIdForUpdate(gameId) → ACTIVE check → candle check → participant check → 
server-authoritative price from CandleService → position validation (O(1) snapshot) →
tradeRepository.save() → positionStore.applyTrade() (O(1) incremental update)
```

**What I verified:**
- PESSIMISTIC_WRITE on Game row prevents ghost-trade race: if game transitions to FINISHED, the trade blocks until FINISHED commits, then the ACTIVE check rejects it
- Price is server-authoritative: `candleService.getCurrentPrice()` — client's price field is IGNORED
- userId is extracted from authenticated Principal, not from client payload → no spoofing
- Position validation against snapshot: BUY checks cash, SELL checks long shares, COVER checks short shares AND cash
- TradeProcessingPipeline currently runs **synchronously** (`tradeTask.run()` inline) — the async backpressure was removed ("Backpressure and heap guard logic removed for lightweight config")

**TradeRateLimiter:** Bucket4j, 5 tokens/sec per player per game, ConcurrentHashMap with CAS atomics.

**Verdict:** ✅ SAFE

---

### 8. POSITION SNAPSHOTS

**What I verified (PositionSnapshotStore.java):**
- Store: `ConcurrentHashMap<String, PlayerPosition>` keyed by "gameId:userId"
- `applyTrade()`: deep copy → mutate → `snapshots.put(k, newPos)` — copy-on-write, readers never see partial state
- `updateMarkPrice()`: only deep-copies if equity changed (optimization) — same atomic swap
- `initializePosition()`: respects maxSnapshots bound (20,000 default = 10K concurrent games × 2 players)
- `evictGame()`: removes all entries matching gameId prefix — called on endMatch, autoFinish, abandon
- Trade logic in applyTrade matches calculatePosition (replay) matches replayWithStats:
  - BUY: `avgCostBasis = (prevBasis * prevShares + cost) / newShares`
  - SELL: profitable if `price > avgCostBasis && avgCostBasis > 0`
  - SHORT: `avgShortPrice = (prevAvg * prev + cost) / newQty`
  - COVER: profitable if `price < avgEntry`

**Verdict:** ✅ SAFE

---

### 9. SCORING

**ScoringUtil.calculate (ScoringUtil.java):**
```
finalScore = 0.60 × profitScore + 0.20 × riskScore + 0.20 × accuracyScore
profitScore   = (profitPct clamped [-100, +100], mapped to 0–100)
riskScore     = (1 − maxDrawdown) × 100
accuracyScore = profitableTrades / totalTrades × 100  (0 trades → 0)
```

**What I verified:**
- Used in: `endMatch()`, `autoFinishGame()`, `forceFinishOnAbandon()` — all three paths
- All three paths compute identical scoring: equity at mark price → ScoringUtil.calculate → winner determined by score comparison
- Winner gets actualScore=1.0, loser gets 0.0, draw gets 0.5

**Verdict:** ✅ SAFE

---

### 10. ELO

**EloUtil.calculateNewRating (EloUtil.java):**
```
expected = 1 / (1 + 10^((opponentRating − playerRating) / 400))
newRating = round(oldRating + 32 × (actualScore − expected))
return max(100, newRating)  // floor at 100
```

**What I verified:**
- Applied in all three end paths: `endMatch()`, `autoFinishGame()`, `forceFinishOnAbandon()`
- `forceFinishOnAbandon()`: disconnected player always gets actualScore=0.0 (loss), remaining player gets 1.0 (win) — **ELO exploit fixed** ✅
- Rating deltas saved on Game entity (`creatorRatingDelta`, `opponentRatingDelta`)
- Users saved to DB (`userRepository.save(creator)`, `userRepository.save(opponent)`)
- All inside @Transactional with findByIdForUpdate → atomic

**Verdict:** ✅ SAFE

---

### 11. REMATCH

**Call chain:** `MatchService.requestRematch()` → `pendingRematches.putIfAbsent(oldGameId, userId)`

**First requester:** putIfAbsent returns null → WS notification to opponent → return `PENDING`  
**Same requester again:** putIfAbsent returns their own ID → idempotent → return `PENDING`  
**Second (different) requester:** putIfAbsent returns first requester's ID → mutual consent → `pendingRematches.remove(oldGameId)` → create new ACTIVE game → `afterCommit { room + candles + scheduler + positions + WS }`

**What I verified:**
- ConcurrentHashMap.putIfAbsent is atomic → race between two simultaneous requests: exactly one sees null (becomes PENDING), the other sees the first requester's ID (ACCEPTED)
- New game creation and all side effects are in afterCommit ✅
- pendingRematches is cleaned up on ACCEPTED

**Critical finding — JVM-local only:**  
`pendingRematches` is a `ConcurrentHashMap` field on the `MatchService` bean. In multi-instance deployment:
- Player A's rematch request hits instance 1 → `putIfAbsent` → null → PENDING
- Player B's rematch request hits instance 2 → `putIfAbsent` → null → PENDING (different map!)
- **Neither ever sees ACCEPTED. No game is created. Rematch silently fails.**

**Verdict:** ✅ SAFE (single instance) / ❌ BROKEN (multi-instance — mutual consent state is not distributed)

---

### 12. DISCONNECT + ELO PENALTY

**Call chain:** `WebSocketEventListener.handleDisconnect()` → `roomManager.unregisterSession()` → check ACTIVE → `markDisconnected()` (Redis) → schedule `handleReconnectTimeout()` at +15s → if not reconnected → `doAbandon()`

**doAbandon (WebSocketEventListener.java ~L195-L240):**
```
matchSchedulerService.stopProgression(gameId)
matchService.forceFinishOnAbandon(gameId, userId)  ← @Transactional + findByIdForUpdate
roomManager.cancelAllReconnectTimers(gameId)
roomManager.endGame(gameId, true)
broadcaster.sendToGame(gameId, "player-disconnected", ...)
```

**forceFinishOnAbandon (MatchService.java ~L490-L590):**
```
findByIdForUpdate → ACTIVE check → currentPrice → updateMarkPrice → getPlayerPosition
→ snapshotEquity → ScoringUtil.calculate → persistStats
→ winner = remaining player (disconnected = LOSER)
→ ELO: disconnected gets actualScore=0.0, remaining gets 1.0
→ save ABANDONED → evict caches
```

**What I verified:**
- `forceFinishOnAbandon` correctly handles the case where game has no opponent (just marks ABANDONED, no scoring)
- Pessimistic lock prevents race with `endMatch` or `autoFinishGame`
- If game is already non-ACTIVE, returns early (idempotent)
- Fallback in doAbandon: if forceFinishOnAbandon throws, sets status ABANDONED directly + evicts

**Verdict:** ✅ SAFE

---

### 13. REDIS CLEANUP

**endGame flow (RoomManager.java ~L175-L210):**
1. `store.setPhase(gameId, "FINISHED"/"ABANDONED")` — Redis HSET
2. Cancel local `ScheduledFuture` (if this instance owns it)
3. `store.releaseScheduler(gameId)` — Redis DEL (only if this instance is owner)
4. `cleanupLocalSessions(gameId)` — removes sessionToGame, sessionToUser, gameSessions entries
5. `cancelAllReconnectTimers(gameId)` — cancels timers + clears Redis disconnected set
6. `store.deleteRoom(gameId)` — DEL of all 5 Redis keys + SREM from index

**What I verified:**
- Fully idempotent — safe to call from multiple instances/threads
- All 5 Redis key types are deleted: room hash, players set, disconnected set, ready counter, scheduler ownership
- Room is removed from the index set
- Local-only state (sessions, timers, scheduler handles) cleaned per-instance

**Verdict:** ✅ SAFE

---

### 14. STATE RESET (caches evicted on game end)

**endMatch:** `candleService.evict()` + `positionStore.evictGame()` + `rateLimiter.evictGame()` + `roomManager.endGame()` ✅  
**autoFinishGame:** `candleService.evict()` + `positionStore.evictGame()` + `rateLimiter.evictGame()` + `roomManager.endGame()` ✅  
**forceFinishOnAbandon:** `candleService.evict()` + `positionStore.evictGame()` + `rateLimiter.evictGame()` ✅ (roomManager.endGame called by doAbandon caller)

**What I verified:**
- All three end paths evict all caches consistently
- No stale cache entries survive game completion

**Verdict:** ✅ SAFE

---

### 15. WEBSOCKET ROUTING

**GameBroadcaster.sendToGame (GameBroadcaster.java):**
```java
messagingTemplate.convertAndSend(destination, payload);  // local delivery
redisRelay.broadcast(destination, payload);               // cross-instance
```

**What I verified:**
- Local delivery always happens first (via SimpMessagingTemplate)
- Redis relay failure: logged as WARN, local delivery already succeeded → game works on this instance
- `sendToUser()`: same pattern (local + Redis relay)
- `sendErrorToUser()`: local only (no relay needed — errors are instance-local)
- Frontend `useGameSocket.js`: subscribes to 12 topics including rematch-request, rematch-started, player-reconnecting, player-reconnected, scoreboard, error
- Rejoin published on connect: `/app/game/${gameId}/rejoin` → triggers handleRejoin on server

**Verdict:** ✅ SAFE

---

## 10 CRITICAL QUESTIONS — ANSWERED

---

### Q1: If both players send a rematch request simultaneously, how many games are created?

**Answer: Exactly 1 (single instance). Zero (multi-instance).**

**Code evidence:** `MatchService.requestRematch()` uses `pendingRematches.putIfAbsent(oldGameId, userId)`. ConcurrentHashMap.putIfAbsent is atomic — exactly one call returns null (PENDING), the other sees the first requester's ID and triggers ACCEPTED.

**Multi-instance problem:** `pendingRematches` is a JVM-local ConcurrentHashMap. If requests hit different instances, both see putIfAbsent return null → both return PENDING → no ACCEPTED ever fires → no game created.

---

### Q2: Does a disconnected player receive an ELO penalty?

**Answer: YES.**

**Code evidence:** `MatchService.forceFinishOnAbandon()` (line ~530):
```java
boolean disconnectedIsCreator = game.getCreator().getId().equals(disconnectedUserId);
User winner = disconnectedIsCreator ? game.getOpponent() : game.getCreator();
double creatorActual = winner.getId().equals(creator.getId()) ? 1.0 : 0.0;
double opponentActual = winner.getId().equals(opponent.getId()) ? 1.0 : 0.0;
```
Disconnected player always gets actualScore=0.0 (loss). Full ELO update applied and persisted.

---

### Q3: If the scheduler-owning instance crashes, does another instance pick up candle progression?

**Answer: NO. The game stalls.**

**Code evidence:** `RedisRoomStore.tryClaimScheduler()` uses SETNX with 2-min TTL. After crash, the key expires after 2 minutes. But there is **no background job** on any instance that scans for ACTIVE games with no scheduler owner and starts progression for them. The game remains ACTIVE in the database with no candle progression forever.

**Missing:** A reconciliation/recovery service that periodically checks `SELECT * FROM games WHERE status='ACTIVE'` and verifies each has a scheduler owner in Redis.

---

### Q4: If both players disconnect and both reconnect within the grace period, does the game resume correctly?

**Answer: YES — if both reconnect within their independent 15s grace timers.**

**Code evidence:** Each player's disconnect triggers an independent 15s timer (`WebSocketEventListener.handleDisconnect()` → `taskScheduler.schedule(handleReconnectTimeout, +15s)`). On reconnect, `GameWebSocketHandler.handleRejoin()` → `roomManager.clearDisconnected()` → cancels the timer. The candle scheduler is NOT paused during the grace period — it continues ticking. Both players rejoin seamlessly.

**Edge case:** If player A's timer fires before player B reconnects, the game is abandoned with A as the loser, even if B is also disconnected. The timers are independent, not coordinated.

---

### Q5: Can a player spam trades to crash the server?

**Answer: NO. Three layers of protection.**

**Code evidence:**
1. **Rate limiter** (TradeRateLimiter.java): Bucket4j, 5 tokens/sec per player per game. Checked before any DB work.
2. **Payload validation** (GameWebSocketHandler.validateTradePayload): type must be BUY/SELL/SHORT/COVER, amount 1–100,000, symbol alphanumeric max 20 chars.
3. **PESSIMISTIC_WRITE on Game row** (MatchTradeService.placeTrade): serializes concurrent trades at DB level. Status check rejects trades for non-ACTIVE games.

Note: The TradeProcessingPipeline's async backpressure (heap guard, per-game queue limits) was removed ("lightweight config") — trades run synchronously. Under extreme load, this could block the WS thread pool, but the rate limiter (5/sec/player) caps throughput far below danger levels.

---

### Q6: If Redis restarts, what breaks?

**Answer: Room state is lost but the system degrades gracefully.**

**Code evidence:** `ResilientRedisRoomStore` wraps all Redis calls with a circuit breaker (5 failures → 30s cooldown). During outage:
- **Reads** fall back to in-memory `shadowRooms` (populated on every successful Redis read)
- **Scheduler ownership** returns `true` locally (single-instance fallback)
- **Room creation/join** uses local shadow cache

**What breaks on Redis recovery:**
- Shadow cache state may diverge from Redis (Redis is empty after restart)
- Existing games' Redis room keys are gone — `hasRoom()` returns false on Redis, true on shadow
- New room creations succeed in Redis (fresh start) but old rooms exist only in shadow
- The `StateReconciliationService` is mentioned in comments but was not found in the source tree — **reconciliation may be unimplemented**

---

### Q7: With two server instances behind a load balancer, can duplicate matches be created from the same queue?

**Answer: No duplicate matches from the same pair — but the queue itself is broken across instances.**

**Code evidence:** The matchmaking queue (`skipList` + `ticketIndex`) is JVM-local. Each instance has its own independent queue. Redisson RLock prevents two instances from pairing the same duo, but that scenario can't even arise because players on different instances are in different queues and invisible to each other.

**Impact:** If player A enqueues on instance 1 and player B on instance 2, they will **never be matched**. Matchmaking only works correctly when all players hit the same instance (single instance or sticky sessions to one matchmaking node).

---

### Q8: If autoFinishGame is called twice simultaneously, does it corrupt ELO or scores?

**Answer: NO. Pessimistic lock prevents double execution.**

**Code evidence:** `MatchSchedulerService.autoFinishGame()` (line ~L230):
```java
Game game = gameRepository.findByIdForUpdate(gameId).orElse(null);
if (game == null || !"ACTIVE".equals(game.getStatus())) return;
```
The first call acquires PESSIMISTIC_WRITE, scores the game, sets status to FINISHED, commits. The second call blocks on `findByIdForUpdate` until the first commits, then reads status=FINISHED → early return. Same protection in `endMatch()` and `forceFinishOnAbandon()`.

---

### Q9: Can profitableTrades be double-counted?

**Answer: NO. Each SELL/COVER trade is evaluated exactly once.**

**Code evidence:** Three code paths compute profitableTrades identically:
- `PositionSnapshotStore.applyTrade()` — incremental O(1) path used during gameplay
- `MatchTradeService.calculatePosition()` — replay path used on server restart
- `MatchSchedulerService.replayWithStats()` — fallback replay in autoFinishGame

All three use the same logic:
- **SELL:** profitable if `price > avgCostBasis && avgCostBasis > 0` (compares sell price to weighted avg purchase price)
- **COVER:** profitable if `price < avgEntry` (compares cover price to weighted avg short entry)

`avgCostBasis` is maintained as a weighted average across BUY trades, updated on each BUY and cleared when all shares are sold. No double-counting is possible.

---

### Q10: Do all side effects (Redis, WebSocket, scheduler) fire only AFTER the DB transaction commits?

**Answer: MOSTLY — but `createMatch()` is an exception.**

**Code evidence:**

| Method | Side effects after commit? | Notes |
|--------|--------------------------|-------|
| `joinMatch()` | ✅ YES | afterCommit callback |
| `createAutoMatch()` | ✅ YES | afterCommit callback |
| `requestRematch()` (ACCEPTED) | ✅ YES | afterCommit callback |
| `createMatch()` | ❌ NO | `roomManager.createRoom()` called inside TX |
| `startMatch()` | ❌ NO | `loadCandles()` + `startProgression()` inside TX |
| `endMatch()` | ⚠ Cleanup inside TX | `candleService.evict()` + `roomManager.endGame()` inside TX — but these are cleanup/teardown, not creation |
| `autoFinishGame()` | ⚠ Cleanup inside TX | Same as endMatch — cleanup |
| `forceFinishOnAbandon()` | ⚠ Cleanup inside TX | Same pattern |

`createMatch()` (MatchService.java ~L38-L52): calls `roomManager.createRoom()` inside `@Transactional`. If the DB commit fails after the Redis room is created, an orphaned Redis room exists with no matching DB game.

`startMatch()` (MatchService.java ~L340-L380): calls `candleService.loadCandles()`, `matchSchedulerService.startProgression()`, and `positionStore.initializePosition()` inside the TX. However, `startMatch()` only applies to already-ACTIVE games (called post-join), so the commit risk is lower.

---

## SUMMARY TABLE

| # | Subsystem | Verdict | Key Finding |
|---|-----------|---------|-------------|
| 1 | Matchmaking | ⚠ RISK | Queue is JVM-local — broken in multi-instance |
| 2 | Game Creation | ⚠ RISK | `createMatch()` has Redis side effect inside TX |
| 3 | Join Flow | ✅ SAFE | CAS + pessimistic lock + afterCommit |
| 4 | Redis Rooms | ✅ SAFE | Lua scripts, idempotent cleanup |
| 5 | Scheduler Ownership | ⚠ RISK | No crash recovery for orphaned games |
| 6 | Candle Progression | ✅ SAFE | Pessimistic lock prevents skip/double-advance |
| 7 | Trade Execution | ✅ SAFE | Server-auth price, pessimistic lock, rate limit |
| 8 | Position Snapshots | ✅ SAFE | Copy-on-write, bounded memory |
| 9 | Scoring | ✅ SAFE | Consistent across all three end paths |
| 10 | ELO | ✅ SAFE | Penalty on disconnect, atomic with TX |
| 11 | Rematch | ⚠ RISK | JVM-local mutual consent — broken multi-instance |
| 12 | Disconnect | ✅ SAFE | 15s grace + ELO penalty + cleanup |
| 13 | Redis Cleanup | ✅ SAFE | All 5 key types + index, idempotent |
| 14 | State Reset | ✅ SAFE | All end paths evict all caches |
| 15 | WS Routing | ✅ SAFE | Local + Redis relay with fallback |

---

## FINAL VERDICT

**For single-instance deployment: ✅ PRODUCTION-READY**  
All 4 prior bug fixes (rematch, ELO exploit, profitableTrades, snapshot capacity) are verified. The core game lifecycle — matchmaking → join → candle progression → trading → scoring → ELO → cleanup — is correct and race-condition-free.

**For multi-instance deployment: ⚠ NOT READY — 3 blockers:**

1. **Matchmaking queue is JVM-local** — players on different instances never match. Need Redis-backed queue (e.g., Redis Sorted Set for rating-ordered storage) or a single matchmaking coordinator instance.

2. **Rematch mutual consent is JVM-local** — `pendingRematches` ConcurrentHashMap exists only on one instance. Need Redis-backed pending state (e.g., `SETNX tl:rematch:{gameId} {userId}`).

3. **No scheduler crash recovery** — if the scheduler-owning instance dies, the game's candle progression stops permanently. Need a periodic reconciliation job: scan ACTIVE games in DB → check Redis for scheduler owner → if missing, start progression.

**Non-blocking improvement:** `createMatch()` should move `roomManager.createRoom()` to an afterCommit callback (consistent with all other creation paths).
