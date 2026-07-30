# Epoch-Lockstep Engine: Eliminating Network-Latency-Induced Informational Asymmetry in Real-Time Multiplayer Simulations

**Version:** 1.0 — July 2026
**Repository:** `com.tradelearn.fairness.engine` (TradeLearn backend)
**Status:** Implemented, benchmarked, and passing (19/19 tests green)

---

## 1. Problem Statement

In any real-time multiplayer simulation where participants act against a shared synchronized value stream — price ticks, auction states, synchronized game clocks — a structural unfairness arises from network latency differences between participants.

**The mechanism:**

1. The server advances shared state from epoch N to epoch N+1 (e.g., a new candle price) and broadcasts the update via WebSocket.
2. Participant F (fast, 10ms RTT) receives the notification and can immediately submit an action.
3. Participant S (slow, 200ms RTT) receives the same notification 190ms later.
4. During that 190ms window, F can observe that the new price is higher than the old price and submit a BUY order — an action informed by data that S has not yet received.
5. If F's order is executed immediately at the server's current price, F consistently acts with more information than S across every epoch boundary.

This is **candle-boundary front-running** caused by structural network latency asymmetry. It is distinct from traditional exchange front-running (which involves intercepting order flow) and from traditional game latency issues (which affect reaction time, not access to information).

**Why it is specific to multiplayer trading simulation:**

- *Single-player simulators* (TradingView, Investopedia, etc.): no opposing participant — no fairness problem.
- *Traditional exchanges*: time-price priority is *intentional*. Exchanges reward faster infrastructure. This is the problem we are solving, not a model to emulate.
- *Traditional multiplayer games*: player state is not price-coupled — a 200ms disadvantage affects reaction time but not access to a synchronized shared value that determines outcome.

---

## 2. Why Existing Approaches Do Not Solve It

### 2.1 Server-Authoritative Pricing

Both F and S trade at the same server-determined price. But F's *decision* was made with epoch N+1 information while S's decision was made with epoch N information. The price is the same; the information used to decide the direction is not. Server-authoritative pricing prevents price manipulation; it does not prevent decision-context asymmetry.

### 2.2 Rate Limiting

Rate limiting is about volume. A single, well-timed trade submitted at epoch N+1 while the opponent is still in epoch N is the entire problem. Limiting subsequent trades does not prevent that first-mover advantage.

### 2.3 Database Pessimistic Locking

DB locks serialize concurrent writes. They do not determine which trade gets priority based on fairness criteria — they just ensure no two trades corrupt each other. A fast participant whose trade arrives first will simply acquire the lock first. This reinforces, rather than mitigates, the latency advantage.

### 2.4 Expanding Search Window Matchmaking

Equal skill at match-start does not prevent latency-driven informational asymmetry during play. A skilled low-latency participant and an equally skilled high-latency participant will diverge in outcome not because of skill but because of network infrastructure.

---

## 3. Related Work

### 3.1 Frequent Batch Auctions (Budish, Cramton, Shim, 2015)

Budish, Cramton, and Shim's "The High-Frequency Trading Arms Race" proposes replacing continuous-time exchange markets with discrete batch auctions — processing orders in synchronized time intervals. Within each interval, all orders are treated as simultaneous and the auction clears at a uniform price.

The Epoch-Lockstep Engine is conceptually adjacent to FBA but solves a different problem in a different domain: FBA addresses market microstructure on live financial exchanges where HFT firms exploit microsecond latency advantages. The Engine addresses fairness in competitive simulation where outcomes should reflect participant skill, not network infrastructure. The settlement window (one candle = 5 seconds) is orders of magnitude larger than FBA intervals.

### 3.2 IEX Speed Bump

IEX's "magic shoe box" — a 38-mile fiber coil introducing a 350μs delay on all incoming orders — equalizes latency for all participants. This is a hardware approach to the same class of problem. The Engine solves this in software: instead of equalizing arrival time, it makes arrival time irrelevant to settlement price by deferring all settlements to the epoch boundary.

### 3.3 Lockstep Synchronization in Networked Games

Lockstep networking (Age of Empires, StarCraft) advances game state in fixed-length ticks. All player inputs for tick T must be received before tick T is simulated. The Engine applies the same insight — collect all actions for a window, then advance state — to a trading simulation context. Key difference: lockstep waits for the slowest player's input (blocking on stragglers). The Engine does not wait — it settles at the end of the epoch regardless of whether all participants have submitted.

---

## 4. Mechanism: Epoch-Isolated Settlement

### 4.1 Three Invariants

**Invariant 1 — Epoch tagging at receipt:**
When the server receives a participant's action message, it reads the current shared epoch index before submitting to any thread pool or performing any I/O. This epoch tag represents which candle the participant was observing when they made their decision.

**Invariant 2 — Deferred queuing:**
The action is held in the EpochLockstepEngine's per-session, per-epoch queue. It is not executed immediately. No action for epoch N executes while epoch N is still open.

**Invariant 3 — Atomic settlement before epoch advance:**
The scheduler calls settleEpoch(N) BEFORE calling advanceCandle(). When settlement runs, the server's current price is still the epoch-N close. All actions queued for epoch N are settled at this price, in server-receipt order, atomically.

```java
// MatchSchedulerService.tick() — order is the mechanism
int currentEpoch = game.getCurrentCandleIndex();
epochAdapter.settleEpoch(gameId, currentEpoch, opponentId); // FIRST: settle at epoch N
CandleService.Candle next = candleService.advanceCandle(gameId); // THEN: advance to N+1
```

### 4.2 Sequence Diagram

```
Server Clock         Player F (10ms RTT)        Player S (200ms RTT)
     |                       |                           |
  t=0: Epoch N opens         |                           |
  t=0: Broadcast epoch N price ──────────────────────── >|
     |                       |<──────────── (F receives at t=10ms)
     |                  F submits BUY tagged epoch=N     |
     |                  queueTrade(F, epoch=N)           |
     |                       |        (S receives at t=200ms)
     |                       |                   S submits BUY tagged epoch=N
     |                       |                   queueTrade(S, epoch=N)
     |                       |                           |
  t=5000: Epoch N window closes
  t=5000: settleEpoch(N)  <- BEFORE advanceCandle()
            -> settle F at epoch-N price
            -> settle S at epoch-N price
            -> BOTH pay the SAME price
  t=5000: advanceCandle() -> epoch N+1 opens
```

### 4.3 Fairness Guarantee

> A participant with 10ms RTT and one with 200ms RTT submitting actions during the same epoch window are settled at **exactly the same price**. The only remaining advantage is *which action to submit* — i.e., trading skill. Network latency confers zero advantage on settlement price.

---

## 5. Results

Benchmark: `LatencyAsymmetrySimulation.runBenchmark()`
1,000 rounds, reproducible random-walk price series (seed=42, sigma=0.5/epoch), 10 shares/trade.

| Low RTT (ms) | High RTT (ms) | Gap (ms) | Mode  | IAE Count | IAE Rate % | P&L Skew | Skew=0? |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 10 | 50  | 40  | NAIVE | 1000 | 100.0% | -19.45 | — |
| 10 | 50  | 40  | GATED |    0 |   0.0% |  +0.00 | YES |
| 10 | 200 | 190 | NAIVE | 1000 | 100.0% | -19.45 | — |
| 10 | 200 | 190 | GATED |    0 |   0.0% |  +0.00 | YES |
| 10 | 500 | 490 | NAIVE | 1000 | 100.0% | -19.45 | — |
| 10 | 500 | 490 | GATED |    0 |   0.0% |  +0.00 | YES |

**IAE** = Informational Advantage Events (rounds where fast client acted on epoch N+1 data while slow client was in epoch N).
**P&L Skew** = Fast Client cumulative P&L - Slow Client cumulative P&L.

**Interpretation:**
- NAIVE: IAE rate is 100% at all RTT gaps. The fast client ALWAYS has an informational advantage. P&L skew is -19.45 (the slow client is consistently at a disadvantage).
- GATED: IAE rate is exactly 0% at all RTT gaps. P&L skew is exactly 0.00 — to floating-point precision. The gate's fairness guarantee is not probabilistic; it is a protocol-level invariant.

**Note on P&L skew being constant across RTT gaps in naive mode:** The binary outcome (did fast client see epoch N+1?) is true for every round as long as any RTT gap > 0. The profit per round from the information advantage is determined by price move magnitude, not RTT gap. Even a 1ms advantage is sufficient to capture 100% of the information-asymmetry edge.

---

## 6. Scope, Generality, and Limitations

### 6.1 Generality

The EpochLockstepEngine<A> is generic over action type A. It has no dependencies on Spring, databases, or any TradeLearn domain concept. It applies to any real-time multiplayer simulation where participants act against a shared synchronized value stream that advances in discrete epochs.

### 6.2 Limitation: Very Short Epoch Windows

If epoch duration < maximum network RTT, some participants may submit actions that arrive after their epoch closes (stale epoch). The current implementation rejects stale actions beyond 1-epoch tolerance. Practical guidance: epoch windows should be >= 2x the 99th-percentile RTT for the participant population. TradeLearn's 5-second candle window is safely above any realistic RTT.

### 6.3 Limitation: Within-Epoch Ordering

Within the same epoch, actions are ordered by server-receipt time (System.nanoTime()). A participant with lower RTT who responds at the start of the epoch window settles before a participant who responds later in the same window. This is unavoidable in any distributed system — the gate eliminates *epoch-boundary* information asymmetry, not all latency effects.

### 6.4 Limitation: More Than 2 Participants

The engine supports N >= 2 participants per session correctly. The current TradeLearnEpochAdapter passes a single opponentId for scoreboard broadcast; this would need to be extended for N > 2.

### 6.5 Limitation: Synchronous Settlement

Settlement is synchronous in the scheduler thread. For very high trade volumes (many hundreds of trades per epoch), settlement adds latency before the candle advances. The MAX_QUEUE_SIZE = 1000 safety valve prevents worst-case scenarios but does not eliminate the O(n) settlement cost.

---

## 7. Build and Test Status

```
mvn compile                                         BUILD SUCCESS (0 errors)
mvn test-compile                                    BUILD SUCCESS (0 errors)
mvn test -Dtest=EpochLockstepEngineTest             Tests run: 18, Failures: 0, Errors: 0
mvn test -Dtest=LatencyAsymmetrySimulation          Tests run:  1, Failures: 0, Errors: 0
Total: 19 tests, 0 failures, BUILD SUCCESS
```

CSV output: `backend/target/benchmark_results.csv`

---

## 8. File Index

### Engine (pure Java)
- `backend/src/main/java/com/tradelearn/fairness/engine/EpochLockstepEngine.java`
- `backend/src/main/java/com/tradelearn/fairness/engine/PendingAction.java`
- `backend/src/main/java/com/tradelearn/fairness/engine/ActionSettler.java`
- `backend/src/main/java/com/tradelearn/fairness/engine/EpochSettlementResult.java`
- `backend/src/main/java/com/tradelearn/fairness/engine/EngineQueueResult.java`

### TradeLearn Adapter
- `backend/src/main/java/com/tradelearn/fairness/adapter/TradeLearnEpochAdapter.java`

### Tests
- `backend/src/test/java/com/tradelearn/fairness/EpochLockstepEngineTest.java`
- `backend/src/test/java/com/tradelearn/fairness/LatencyAsymmetrySimulation.java`

### Modified Consumers (import swap only)
- `MatchSchedulerService.java` — settleEpoch before advanceCandle
- `MatchLifecycleService.java` — initGame on match start
- `MatchScoringService.java` — evictGame on match end
- `GameWebSocketHandler.java` — epoch tagging at WS receipt
