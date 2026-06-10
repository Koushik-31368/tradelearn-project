# TradeLearn — Load Test Plan & Bottleneck Detection Guide

## Table of Contents
1. [Test Data Seeding Strategy](#1-test-data-seeding-strategy)
2. [Metrics to Capture](#2-metrics-to-capture)
3. [Failure Thresholds](#3-failure-thresholds)
4. [Bottleneck Detection Strategy](#4-bottleneck-detection-strategy)
5. [Thread Pool Tuning from Results](#5-thread-pool-tuning-from-results)
6. [Memory Leak Detection in PositionSnapshotStore](#6-memory-leak-detection-in-positionsnapshotstore)
7. [Execution Runbook](#7-execution-runbook)

---

## 1. Test Data Seeding Strategy

### Why Pre-Seeding Is Required
The load test needs 10,000 users to exist before game creation begins. Registration rate limits (100 RPM) and bcrypt hashing latency make inline registration a bottleneck.

### Seed Execution
```bash
# 1. Deploy infrastructure
kubectl apply -f loadtest/k8s/loadtest-infra.yaml

# 2. Wait for backend readiness
kubectl -n loadtest wait --for=condition=ready pod -l app=tradelearn-backend --timeout=120s

# 3. Run seed job (creates 10K users in ~3 minutes)
kubectl -n loadtest create job k6-seed --from=job/k6-seed

# 4. Verify
kubectl -n loadtest logs job/k6-seed -f
```

### Seed Design
- **User schema**: `loadtest_user_{0..9999}@tradelearn.test` / `lt_user_{i}` / `LoadTest1{i}`
- **Pairing**: Even-indexed users (`0,2,4,...`) are match creators; odd-indexed (`1,3,5,...`) are opponents
- **Idempotency**: Script handles 409 (already exists) as success — safe to re-run
- **Parallelism**: 50 concurrent VUs × 200 iterations each = 10,000 registrations
- **Verification**: k6 threshold requires ≥99% success (`users_seeded >= 9900`)

### Database Pre-Warming
After seeding, warm the PostgreSQL buffer cache and connection pool:
```bash
kubectl -n loadtest exec deploy/tradelearn-backend -- \
  curl -s http://localhost:8080/api/users/leaderboard > /dev/null

kubectl -n loadtest exec deploy/tradelearn-backend -- \
  curl -s http://localhost:8080/api/health
```

---

## 2. Metrics to Capture

### Application Layer (Spring Boot Actuator → Prometheus)

| Metric | PromQL | What It Reveals |
|--------|--------|-----------------|
| **Trade p95 latency** | `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/match/trade"}[30s]))` | End-to-end trade processing time including DB write + pessimistic lock wait |
| **Trade p99 latency** | `histogram_quantile(0.99, ...)` | Tail latency — lock contention or GC pauses |
| **Trade throughput** | `sum(rate(http_server_requests_seconds_count{uri="/api/match/trade"}[30s]))` | Should sustain ~50K trades/sec at peak (10K users × 5/sec) |
| **Trade error rate** | `sum(rate(...{status=~"5.."}[1m])) / sum(rate(...[1m]))` | Server-side failures (lock timeouts, pool exhaustion) |
| **WebSocket sessions** | `spring_websocket_sessions_active` | Should plateau at ~10K during sustained phase |
| **STOMP inbound queue** | Queue size on `clientInboundChannel` | Backpressure indicator — should stay < 500 |
| **STOMP outbound queue** | Queue size on `clientOutboundChannel` | Broadcast bottleneck — candle/scoreboard fanout |

### JVM Layer

| Metric | PromQL | Threshold |
|--------|--------|-----------|
| **Heap used** | `jvm_memory_used_bytes{area="heap"}` | Must not monotonically increase (leak signal) |
| **GC pause total/min** | `increase(jvm_gc_pause_seconds_sum[1m])` | < 1s/min for G1GC at 4GB heap |
| **GC pause max** | `jvm_gc_pause_seconds_max` | < 200ms (single pause) |
| **GC count/min** | `increase(jvm_gc_pause_seconds_count[1m])` | Young GC frequent is OK; Full GC > 0 is a problem |
| **Threads** | `jvm_threads_live_threads` | Should stabilize; monotonic increase = thread leak |
| **CPU (process)** | `process_cpu_usage` | Per-pod, should stay < 70% of limit |
| **Direct buffers** | `jvm_buffer_memory_used_bytes{id="direct"}` | WebSocket NIO buffers — watch for OOM |

### Database Layer

| Metric | PromQL | Threshold |
|--------|--------|-----------|
| **Active connections** | `hikaricp_connections_active` | Must stay < `maximum-pool-size` (80) |
| **Pending connections** | `hikaricp_connections_pending` | > 0 sustained = pool too small |
| **Connection acquire time** | `hikaricp_connections_acquire_seconds` | p95 < 50ms |
| **Query duration** | `hikaricp_connections_usage_seconds` | p95 < 100ms |

### Redis Layer

| Metric | Source | Threshold |
|--------|--------|-----------|
| **Command latency** | `redis_commands_duration_seconds_total / redis_commands_processed_total` | Avg < 1ms, p99 < 5ms |
| **Connected clients** | `redis_connected_clients` | Should match backend replica count × lettuce pool max (3×16=48) |
| **Memory used** | `redis_memory_used_bytes` | Must stay < `maxmemory` (2GB) |
| **Evicted keys** | `redis_evicted_keys_total` | Must be 0 (evictions = data loss) |
| **Keyspace hits/misses** | `redis_keyspace_hits_total / (hits + misses)` | Hit rate > 95% |

### k6 Client-Side

| Metric | k6 Name | Threshold |
|--------|---------|-----------|
| **Trade latency p95** | `trade_latency_ms` | < 200ms |
| **WS message latency p95** | `ws_message_latency_ms` | < 100ms |
| **Trade success rate** | `trade_success_rate` | > 95% |
| **WS connect success** | `ws_connect_success_rate` | > 90% |
| **HTTP error rate** | `http_req_failed` | < 5% |

---

## 3. Failure Thresholds

### Hard Failures (Abort Test)

| Condition | Detection | Action |
|-----------|-----------|--------|
| Trade p99 > 2s sustained 2min | k6 threshold + Prometheus alert | Abort; investigate DB locks |
| Trade success rate < 80% | k6 threshold | Abort; check error logs |
| Backend pod OOMKilled | `kube_pod_container_status_terminated_reason{reason="OOMKilled"}` | Abort; increase memory or fix leak |
| PostgreSQL connection refused | HikariCP `connection-timeout` exceptions | Abort; DB down |
| Redis circuit breaker OPEN | Application logs + `tradelearn_redis_circuit_state` | Continue (graceful degradation) but flag |

### Soft Failures (Flag, Continue)

| Condition | Detection | Implication |
|-----------|-----------|-------------|
| Trade p95 > 200ms | Prometheus alert | Approaching SLO breach |
| GC pause > 200ms single | `jvm_gc_pause_seconds_max` | Heap pressure — may cascade |
| Tomcat threads > 90% utilized | `tomcat_threads_busy / max > 0.9` | Approaching thread starvation |
| HikariCP pending > 0 for > 30s | `hikaricp_connections_pending > 0` | Pool undersized for load |
| PositionSnapshotStore > 85% | `snapshot_count / 20000 > 0.85` | Games not evicting properly |
| WebSocket sessions drop > 100/5min | `delta(sessions_active[5m]) < -100` | Network or backpressure issue |

### k6 Built-in Thresholds (from script)
```javascript
thresholds: {
  "trade_latency_ms":        ["p(95)<200", "p(99)<500", "max<2000"],
  "ws_message_latency_ms":   ["p(95)<100", "p(99)<300"],
  "trade_success_rate":      ["rate>0.95"],
  "ws_connect_success_rate": ["rate>0.90"],
  "http_req_failed":         ["rate<0.05"],
  "http_req_duration":       ["p(95)<1000"],
  "trades_placed_total":     ["count>500000"],
}
```

---

## 4. Bottleneck Detection Strategy

### 4.1 Systematic Isolation: The USE Method

For each resource (CPU, Memory, Network, Disk, DB Pool, Thread Pool, Redis, Queue), measure:
- **U**tilization — fraction of time busy
- **S**aturation — queue depth / pending work
- **E**rrors — failed operations

### 4.2 Bottleneck Decision Tree

```
Trade p95 > 200ms?
├── YES → Check Tomcat thread utilization
│   ├── > 90% → Thread pool exhaustion (§5)
│   │   └── Check: are threads blocked on DB locks?
│   │       ├── YES → Pessimistic lock on Game row is the bottleneck
│   │       │   └── Solution: Reduce lock scope, batch trades, or shard games
│   │       └── NO → Tomcat threads too low for WS + HTTP combined
│   │           └── Solution: Increase server.tomcat.threads.max
│   └── < 90% → Check HikariCP
│       ├── Pending > 0 → DB connection pool exhaustion
│       │   └── Check avg query time
│       │       ├── > 50ms → Slow queries (missing index, lock waits)
│       │       │   └── Run: EXPLAIN ANALYZE on trade INSERT + game SELECT FOR UPDATE
│       │       └── < 50ms → Pool too small
│       │           └── Solution: Increase hikari.maximum-pool-size
│       └── Pending = 0 → Check GC
│           ├── GC pause > 200ms → Heap pressure
│           │   └── Check PositionSnapshotStore size + heap usage pattern
│           │       ├── Monotonic heap growth → Memory leak (§6)
│           │       └── Sawtooth pattern → Normal; tune G1 region size
│           └── GC OK → Check Redis latency
│               ├── > 5ms avg → Redis bottleneck
│               │   └── Check: connected_clients, memory, command rate
│               └── < 5ms → Check STOMP channel queues
│                   ├── Queue > 0 growing → Message processing backpressure
│                   │   └── Solution: Increase inbound/outbound channel threads
│                   └── Queue stable → Network saturation (pod bandwidth)
└── NO → System healthy at current load; ramp higher
```

### 4.3 Critical Bottleneck: Pessimistic Lock on Game Row

The `PESSIMISTIC_WRITE` lock in `placeTrade()` serializes all trades within a single game. With 2 players × 5 trades/sec = 10 trades/sec/game, each trade must complete within 100ms to avoid queuing.

**How to detect**:
```sql
-- Run against PostgreSQL during the test
SELECT pid, state, wait_event_type, wait_event, query,
       now() - query_start AS duration
FROM pg_stat_activity
WHERE state = 'active' AND wait_event_type = 'Lock'
ORDER BY duration DESC;
```

**Prometheus query for lock contention**:
```promql
# If trade latency spikes while DB pool is NOT full → lock contention
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket{uri="/api/match/trade"}[30s])) > 0.5
  AND hikaricp_connections_pending == 0
```

### 4.4 Profiling Commands

```bash
# Thread dump (find blocked threads)
kubectl -n loadtest exec deploy/tradelearn-backend -- \
  jcmd 1 Thread.print > /tmp/threaddump_$(date +%s).txt

# Heap histogram (top memory consumers)
kubectl -n loadtest exec deploy/tradelearn-backend -- \
  jcmd 1 GC.class_histogram | head -30

# Flight Recorder (60s capture)
kubectl -n loadtest exec deploy/tradelearn-backend -- \
  jcmd 1 JFR.start duration=60s filename=/tmp/loadtest.jfr settings=profile

# Copy JFR out
kubectl -n loadtest cp deploy/tradelearn-backend:/tmp/loadtest.jfr ./loadtest.jfr

# GC log analysis
kubectl -n loadtest cp deploy/tradelearn-backend:/tmp/gc.log ./gc.log
# Then: https://gceasy.io or gcviewer
```

### 4.5 Network-Level

```bash
# Check pod bandwidth
kubectl -n loadtest exec deploy/tradelearn-backend -- \
  cat /sys/class/net/eth0/statistics/rx_bytes /sys/class/net/eth0/statistics/tx_bytes

# WebSocket frame rate estimation
# 10K users × (candle + scoreboard per 5s) = 4K broadcasts/sec
# Each broadcast ~2KB → 8 MB/s outbound per pod (with 3 replicas)
```

---

## 5. Thread Pool Tuning from Results

### 5.1 Tomcat HTTP Thread Pool

**Current**: `server.tomcat.threads.max=200` (prod), `400` (load test)

**Formula**:
```
optimal_threads = target_concurrency × avg_response_time_sec
                = (10000 users × 5 req/sec) / 3 replicas × 0.05s avg
                = 833 threads/replica
```

But this ignores that most concurrency is WebSocket (long-lived), not HTTP request-response. WebSocket connections don't consume Tomcat threads after the upgrade handshake.

**Tuning procedure**:
1. During the sustained phase, query:
   ```promql
   tomcat_threads_busy_threads / tomcat_threads_config_max_threads
   ```
2. If utilization > 85% sustained:
   ```properties
   server.tomcat.threads.max=600
   ```
3. If utilization < 30%:
   ```properties
   server.tomcat.threads.max=200  # reduce to save memory (1MB stack/thread)
   ```
4. Each thread costs ~1MB of stack memory. At 600 threads = 600MB just for stacks.

### 5.2 STOMP Inbound Channel (WebSocket Message Processing)

**Current**: 32 core, 128 max, queue 500

This is the critical path for WebSocket trades. Each `/app/game/{gameId}/trade` message passes through this pool.

**Tuning procedure**:
1. Monitor queue depth:
   ```promql
   spring_integration_channel_queue_size{name="clientInboundChannel"}
   ```
2. If queue grows monotonically:
   ```java
   // In WebSocketConfig
   config.setTaskExecutor(taskExecutor(64, 256, 1000));
   ```
3. If queue stays near 0 but threads are maxed:
   ```java
   // CPU-bound — each message triggers DB write
   // Solution: increase core pool, not just max
   config.setTaskExecutor(taskExecutor(128, 256, 1000));
   ```

### 5.3 STOMP Outbound Channel (Broadcast to Subscribers)

**Current**: 16 core, 64 max, queue 256

Broadcasts candle updates + scoreboard to all subscribers in a game. With 1,000 games × 2 topics × 2 players = 4,000 sends every 5 seconds.

**Tuning procedure**:
1. If WebSocket message latency p95 > 100ms but trade latency is fine → outbound bottleneck
2. Increase: `taskExecutor(32, 128, 500)`

### 5.4 Scheduler Pool (Candle Tick Progression)

**Current**: 64 threads

Each active game has a 5-second scheduled task that advances the candle. With 1,000 games:

```
required = games / tick_interval × avg_tick_duration
         = 1000 / 5s × 0.05s = 10 threads minimum
```

64 is generous. Only increase if you see candle delays:
```promql
# If candle broadcasts lag behind the 5s schedule
rate(tradelearn_candle_tick_duration_seconds_sum[1m]) / rate(tradelearn_candle_tick_duration_seconds_count[1m]) > 0.5
```

### 5.5 HikariCP Database Pool

**Current**: max=50 (prod), 80 (load test)

**Tuning procedure**:
1. If `hikaricp_connections_pending > 0` for > 30s:
   ```properties
   spring.datasource.hikari.maximum-pool-size=120
   ```
2. But verify PostgreSQL can handle it:
   ```
   pg max_connections (300) > replicas (3) × pool (120) = 360  ← TOO HIGH
   ```
   Either increase PG `max_connections` or use PgBouncer.

3. If connections are active but latency is fine → queries are slow, not pool-limited:
   ```sql
   SELECT query, calls, mean_exec_time, total_exec_time
   FROM pg_stat_statements
   ORDER BY mean_exec_time DESC LIMIT 20;
   ```

### 5.6 Thread Pool Tuning Summary Table

| Pool | Starting Value | Metric to Watch | Scale Up If | Scale Down If |
|------|---------------|-----------------|-------------|---------------|
| Tomcat | 400 | `busy/max > 0.85` | Utilization > 85% for 2+ min | Utilization < 30% for 5+ min |
| STOMP inbound | 32-128, q500 | Queue depth > 100 | Queue growing monotonically | Queue always 0, threads < 10% |
| STOMP outbound | 16-64, q256 | WS message latency p95 | p95 > 100ms | p95 < 20ms, queue always 0 |
| Scheduler | 128 | Candle tick lag | Tick duration > 500ms | Tick duration < 50ms consistently |
| HikariCP | 80 | Pending > 0 | Pending > 0 for 30s+ | Active < 20% of max |

---

## 6. Memory Leak Detection in PositionSnapshotStore

### 6.1 The Risk

`PositionSnapshotStore` uses a `ConcurrentHashMap<String, PlayerPosition>` keyed by `"gameId:userId"`. If `evictGame()` is never called (bug in game completion flow, exception during `endMatch`, or orphaned games), entries accumulate indefinitely.

Additionally, each `applyTrade()` creates a full deep copy of the position. If references are leaked (e.g., returned to a caller that caches them), old copies won't be GC'd.

### 6.2 Detection: Snapshot Count Monitoring

**Step 1**: Expose the snapshot count as a Micrometer gauge (if not already):

```java
// In PositionSnapshotStore constructor, register a gauge:
@Service
public class PositionSnapshotStore {
    public PositionSnapshotStore(
            @Value("${tradelearn.snapshot.max-entries:20000}") int maxSnapshots,
            MeterRegistry meterRegistry) {
        this.maxSnapshots = maxSnapshots;
        // Register gauge
        meterRegistry.gauge("tradelearn.position.snapshot.count", snapshots, ConcurrentHashMap::size);
    }
}
```

**Step 2**: Prometheus query to detect leaks:
```promql
# Snapshot count should correlate with active games (2 snapshots per game)
# If it grows while active games stay flat → leak

# Leak detection: snapshot count increasing while active games stable
deriv(tradelearn_position_snapshot_count[10m]) > 0
  AND deriv(tradelearn_active_games[10m]) <= 0

# Ratio check: should be ~2.0 (2 players per game)
tradelearn_position_snapshot_count / (tradelearn_active_games * 2) > 2.5
```

**Step 3**: Grafana alert rule:
```yaml
- alert: SnapshotStoreLeak
  expr: |
    increase(tradelearn_position_snapshot_count[10m]) > 100
    AND increase(tradelearn_active_games[10m]) <= 0
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "PositionSnapshotStore growing without new games — probable leak"
```

### 6.3 Detection: Heap Dump Analysis

```bash
# 1. Take a heap dump during the sustained phase
kubectl -n loadtest exec deploy/tradelearn-backend -- \
  jcmd 1 GC.heap_dump /tmp/heap_t1.hprof

# 2. Wait 10 minutes, take another
kubectl -n loadtest exec deploy/tradelearn-backend -- \
  jcmd 1 GC.heap_dump /tmp/heap_t2.hprof

# 3. Copy both out
kubectl -n loadtest cp deploy/tradelearn-backend:/tmp/heap_t1.hprof ./heap_t1.hprof
kubectl -n loadtest cp deploy/tradelearn-backend:/tmp/heap_t2.hprof ./heap_t2.hprof

# 4. Analyze with Eclipse MAT or VisualVM
# Look for:
#   - ConcurrentHashMap$Node count growth between dumps
#   - PlayerPosition instance count vs expected (active_games × 2)
#   - Retained heap of PositionSnapshotStore
#   - HashMap instances inside PlayerPosition (shares, shortShares, avgShortPrice)
```

**Eclipse MAT OQL queries**:
```sql
-- Count PlayerPosition instances
SELECT COUNT(*) FROM com.tradelearn.server.service.MatchTradeService$PlayerPosition

-- Find the ConcurrentHashMap in PositionSnapshotStore
SELECT s.snapshots.size FROM com.tradelearn.server.service.PositionSnapshotStore s

-- List all keys (to find orphaned game IDs)
SELECT toString(entry.key) FROM java.util.concurrent.ConcurrentHashMap$Node entry
  WHERE toString(entry.key) LIKE "%:%"
```

### 6.4 Detection: Class Histogram Diff

```bash
# Take two histograms 10 minutes apart
kubectl -n loadtest exec deploy/tradelearn-backend -- \
  jcmd 1 GC.class_histogram > /tmp/histo_t1.txt
sleep 600
kubectl -n loadtest exec deploy/tradelearn-backend -- \
  jcmd 1 GC.class_histogram > /tmp/histo_t2.txt

# Compare — look for growing counts of:
#   - MatchTradeService$PlayerPosition
#   - java.util.HashMap
#   - java.util.HashMap$Node
#   - ConcurrentHashMap$Node
diff <(head -50 /tmp/histo_t1.txt) <(head -50 /tmp/histo_t2.txt)
```

### 6.5 Detection: Orphan Game ID Cross-Reference

```bash
# During the test, query active games from the DB
kubectl -n loadtest exec deploy/postgres -- \
  psql -U tradelearn -d tradelearn_loadtest -c \
  "SELECT id FROM games WHERE status = 'ACTIVE'" > /tmp/active_games.txt

# Compare with snapshot store keys via JMX or actuator endpoint
# If snapshot store contains keys for games NOT in the active list → orphans
```

### 6.6 Root Causes to Investigate

| Symptom | Root Cause | Fix |
|---------|-----------|-----|
| `snapshotCount` grows monotonically | `evictGame()` never called for finished games | Ensure `endMatch()` always calls `positionStore.evictGame(gameId)` in a `finally` block |
| Snapshot count > active_games × 2 | Games stuck in ACTIVE status (scheduler crash) | Add a scheduled cleanup: evict snapshots for games with status ≠ ACTIVE |
| Heap grows but snapshot count is stable | Deep copies from `applyTrade()` retained by callers | Audit all callers of `applyTrade()` and `getPosition()` — ensure they don't cache the returned `PlayerPosition` |
| High HashMap$Node count | Large position maps (many symbols per player) | Shouldn't happen in 1v1 single-symbol matches; check for symbol key inconsistency (case sensitivity) |

### 6.7 Preventive Safeguard: Scheduled Cleanup

Add a scheduled task to evict stale snapshots as a safety net:

```java
@Scheduled(fixedDelay = 300_000) // every 5 minutes
public void evictStaleSnapshots() {
    Set<Long> activeGameIds = gameRepository.findActiveGameIds();
    int evicted = 0;
    for (String key : snapshots.keySet()) {
        long gameId = Long.parseLong(key.split(":")[0]);
        if (!activeGameIds.contains(gameId)) {
            snapshots.remove(key);
            evicted++;
        }
    }
    if (evicted > 0) {
        log.info("[Position] Evicted {} stale snapshots (store size={})", evicted, snapshots.size());
    }
}
```

---

## 7. Execution Runbook

### Pre-Flight Checklist

```bash
# 1. Deploy infrastructure
kubectl apply -f loadtest/k8s/loadtest-infra.yaml
kubectl -n loadtest wait --for=condition=ready pod --all --timeout=300s

# 2. Verify all components
kubectl -n loadtest get pods
# Expected: 3× tradelearn-backend, 1× postgres, 1× redis, 1× prometheus, 1× grafana

# 3. Open Grafana dashboard
kubectl -n loadtest port-forward svc/grafana 3000:3000 &
# → http://localhost:3000 (admin/loadtest)

# 4. Verify Prometheus targets
kubectl -n loadtest port-forward svc/prometheus 9090:9090 &
# → http://localhost:9090/targets — all should be UP

# 5. Seed users
kubectl -n loadtest create configmap k6-scripts \
  --from-file=loadtest/k6/seed-users.js \
  --from-file=loadtest/k6/tradelearn-load.js
kubectl -n loadtest create -f loadtest/k8s/loadtest-infra.yaml  # seed job
kubectl -n loadtest wait --for=condition=complete job/k6-seed --timeout=600s
```

### Execute Load Test

```bash
# Start the main test
kubectl -n loadtest create -f loadtest/k8s/loadtest-infra.yaml  # loadtest job

# Monitor in real-time
kubectl -n loadtest logs -f job/k6-loadtest

# In parallel, watch Grafana dashboard for:
#   - Trade latency trending up
#   - GC pauses
#   - Snapshot store growth
#   - DB pool saturation
```

### During Test: Key Prometheus Queries

```promql
# 1. Are we hitting our throughput target?
sum(rate(http_server_requests_seconds_count{uri="/api/match/trade"}[1m]))
# Expected: ~16,667/sec peak (10K users × 5/sec ÷ 3 replicas)

# 2. Is the system healthy?
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/match/trade"}[30s]))
# Expected: < 0.2 seconds

# 3. Where's the bottleneck?
topk(5, sum by (uri) (rate(http_server_requests_seconds_sum[1m])))
# Shows which endpoints consume the most time

# 4. Is PositionSnapshotStore leaking?
tradelearn_position_snapshot_count
# Should track: active_games × 2, then decrease as games finish
```

### Post-Test Analysis

```bash
# 1. Collect k6 results
kubectl -n loadtest cp job/k6-loadtest:/results/results.json ./results.json

# 2. Collect thread dumps
for pod in $(kubectl -n loadtest get pods -l app=tradelearn-backend -o name); do
  kubectl -n loadtest exec $pod -- jcmd 1 Thread.print > "threaddump_${pod##*/}.txt"
done

# 3. Collect GC logs
for pod in $(kubectl -n loadtest get pods -l app=tradelearn-backend -o name); do
  kubectl -n loadtest cp "${pod##*/}:/tmp/gc.log" "gclog_${pod##*/}.log"
done

# 4. Collect heap dumps (if leak suspected)
for pod in $(kubectl -n loadtest get pods -l app=tradelearn-backend -o name); do
  kubectl -n loadtest exec $pod -- jcmd 1 GC.heap_dump /tmp/final_heap.hprof
  kubectl -n loadtest cp "${pod##*/}:/tmp/final_heap.hprof" "heap_${pod##*/}.hprof"
done

# 5. PostgreSQL slow query log
kubectl -n loadtest exec deploy/postgres -- \
  psql -U tradelearn -d tradelearn_loadtest -c \
  "SELECT query, calls, mean_exec_time, stddev_exec_time, rows
   FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 20;"

# 6. Clean up
kubectl delete namespace loadtest
```

### Results Interpretation Matrix

| Observation | Diagnosis | Action |
|-------------|-----------|--------|
| Trade p95 < 200ms, throughput meets target | Pass | System handles the load |
| Trade p95 200-500ms, stable | Marginal | Tune thread pools per §5 |
| Trade p95 > 500ms, growing | Fail: contention | Check lock waits, DB pool, GC |
| Trade success rate < 95% | Fail: errors | Check error logs for root cause |
| GC pause > 500ms | Fail: heap pressure | Increase heap or fix leak |
| Snapshot count diverges from games×2 | Leak | Follow §6 procedure |
| WS sessions drop during sustained phase | Backpressure | Increase outbound channel threads |
| DB pending > 10 sustained | Pool exhaustion | Increase pool + verify PG max_connections |
| Redis latency > 10ms | Redis overload | Scale Redis or reduce pub/sub fanout |
