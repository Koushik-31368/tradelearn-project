package com.tradelearn.fairness;

import com.tradelearn.fairness.engine.EpochLockstepEngine;
import com.tradelearn.fairness.engine.EpochSettlementResult;
import com.tradelearn.fairness.engine.EngineQueueResult;
import com.tradelearn.fairness.engine.PendingAction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EpochLockstepEngine}.
 *
 * Tests cover:
 *   - Session lifecycle (init, evict, idempotency)
 *   - Queue acceptance / staleness / capacity rejection
 *   - Settlement correctness: all actions settled, correct ordering, price isolation
 *   - Settlement atomicity: drain-on-remove, no double-settlement
 *   - Thread safety: concurrent queue + settle under load
 *   - Empty epoch handling
 *   - Session-not-found guard
 */
@DisplayName("EpochLockstepEngine — Unit Tests")
class EpochLockstepEngineTest {

    private EpochLockstepEngine<String> engine;

    @BeforeEach
    void setUp() {
        engine = new EpochLockstepEngine<>();
    }

    // ── Session lifecycle ────────────────────────────────────────────────────

    @Test
    @DisplayName("initSession creates session; getCurrentEpoch returns 0")
    void initSession_createsSession() {
        engine.initSession("s1");
        assertEquals(0, engine.getCurrentEpoch("s1"));
        assertTrue(engine.hasSession("s1"));
        assertEquals(1, engine.activeSessionCount());
    }

    @Test
    @DisplayName("initSession is idempotent")
    void initSession_idempotent() {
        engine.initSession("s1");
        engine.initSession("s1"); // second call must not throw or reset epoch
        assertEquals(0, engine.getCurrentEpoch("s1"));
    }

    @Test
    @DisplayName("evictSession removes session; getCurrentEpoch returns -1")
    void evictSession_removesSession() {
        engine.initSession("s1");
        engine.evictSession("s1");
        assertEquals(-1, engine.getCurrentEpoch("s1"));
        assertFalse(engine.hasSession("s1"));
        assertEquals(0, engine.activeSessionCount());
    }

    @Test
    @DisplayName("getCurrentEpoch returns -1 for unknown session")
    void getCurrentEpoch_unknownSession() {
        assertEquals(-1, engine.getCurrentEpoch("nonexistent"));
    }

    // ── Queuing ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("queue returns ACCEPTED for valid action in active session")
    void queue_accepted() {
        engine.initSession("s1");
        EngineQueueResult result = engine.queue(action("s1", "p1", 0));
        assertEquals(EngineQueueResult.ACCEPTED, result);
        assertEquals(1, engine.pendingActionCount("s1"));
    }

    @Test
    @DisplayName("queue returns SESSION_NOT_FOUND if session not initialized")
    void queue_sessionNotFound() {
        EngineQueueResult result = engine.queue(action("ghost", "p1", 0));
        assertEquals(EngineQueueResult.SESSION_NOT_FOUND, result);
    }

    @Test
    @DisplayName("queue rejects actions more than 1 epoch stale")
    void queue_staleEpoch() {
        engine.initSession("s1");
        // Advance to epoch 3 by settling epochs 0, 1, 2
        settle("s1", 0); settle("s1", 1); settle("s1", 2);
        // epoch 3 is current; epoch 1 is exactly 2 behind → stale (tolerance = 1)
        EngineQueueResult result = engine.queue(action("s1", "p1", 1));
        assertEquals(EngineQueueResult.STALE_EPOCH, result);
    }

    @Test
    @DisplayName("queue accepts actions 1 epoch behind current (within tolerance)")
    void queue_withinStaleTolerance() {
        engine.initSession("s1");
        settle("s1", 0); // epoch is now 1
        // epoch 0 is exactly 1 behind → should be accepted
        EngineQueueResult result = engine.queue(action("s1", "p1", 0));
        assertEquals(EngineQueueResult.ACCEPTED, result);
    }

    @Test
    @DisplayName("queue returns QUEUE_FULL when per-epoch cap exceeded")
    void queue_queueFull() {
        EpochLockstepEngine<String> smallEngine = new EpochLockstepEngine<>(1, 3);
        smallEngine.initSession("s1");
        smallEngine.queue(action("s1", "p1", 0));
        smallEngine.queue(action("s1", "p2", 0));
        smallEngine.queue(action("s1", "p3", 0));
        EngineQueueResult result = smallEngine.queue(action("s1", "p4", 0));
        assertEquals(EngineQueueResult.QUEUE_FULL, result);
    }

    // ── Settlement ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("settleEpoch settles all queued actions and advances epoch")
    void settleEpoch_settlesAll() {
        engine.initSession("s1");
        engine.queue(action("s1", "p1", 0));
        engine.queue(action("s1", "p2", 0));
        engine.queue(action("s1", "p3", 0));

        List<String> settled = new ArrayList<>();
        EpochSettlementResult result = engine.settleEpoch("s1", 0, a -> settled.add(a.participantId()));

        assertEquals(3, result.total());
        assertEquals(3, result.settled());
        assertEquals(0, result.rejected());
        assertEquals(1, result.nextEpoch());
        assertEquals(3, settled.size());
        assertEquals(1, engine.getCurrentEpoch("s1"));
    }

    @Test
    @DisplayName("settleEpoch orders actions by receivedNanos ascending")
    void settleEpoch_orderedByReceiptTime() {
        engine.initSession("s1");
        // Queue actions with explicit nanos: p2 earlier than p1
        engine.queue(new PendingAction<>("s1", "p1", 0, 2000L, "action-p1"));
        engine.queue(new PendingAction<>("s1", "p2", 0, 1000L, "action-p2"));

        List<String> order = new ArrayList<>();
        engine.settleEpoch("s1", 0, a -> order.add(a.participantId()));

        assertEquals(List.of("p2", "p1"), order, "Earlier receipt time must settle first");
    }

    @Test
    @DisplayName("settleEpoch handles empty epoch — returns wasEmpty=true")
    void settleEpoch_emptyEpoch() {
        engine.initSession("s1");
        EpochSettlementResult result = engine.settleEpoch("s1", 0, a -> {});
        assertTrue(result.wasEmpty());
        assertEquals(0, result.total());
        assertEquals(1, result.nextEpoch());
    }

    @Test
    @DisplayName("settleEpoch increments epoch even when settler throws")
    void settleEpoch_rejectedActionsCounted() {
        engine.initSession("s1");
        engine.queue(action("s1", "p1", 0));
        engine.queue(action("s1", "p2", 0));

        EpochSettlementResult result = engine.settleEpoch("s1", 0, a -> {
            if ("p1".equals(a.participantId())) throw new RuntimeException("rejected!");
        });

        assertEquals(2, result.total());
        assertEquals(1, result.settled());
        assertEquals(1, result.rejected());
        assertEquals(1, engine.getCurrentEpoch("s1"));  // epoch still advanced
    }

    @Test
    @DisplayName("settleEpoch is atomic — actions cannot be settled twice")
    void settleEpoch_atomicDrain() {
        engine.initSession("s1");
        engine.queue(action("s1", "p1", 0));

        AtomicInteger callCount = new AtomicInteger(0);
        engine.settleEpoch("s1", 0, a -> callCount.incrementAndGet());
        engine.settleEpoch("s1", 0, a -> callCount.incrementAndGet()); // second call: no-op

        assertEquals(1, callCount.get(), "Action must settle exactly once");
    }

    @Test
    @DisplayName("settleEpoch on unknown session returns empty result safely")
    void settleEpoch_unknownSession() {
        EpochSettlementResult result = engine.settleEpoch("ghost", 0, a -> {});
        assertTrue(result.wasEmpty());
        assertEquals(0, result.total());
    }

    @Test
    @DisplayName("Actions queued for future epoch are NOT settled in current epoch")
    void settleEpoch_futureActionsUnaffected() {
        engine.initSession("s1");
        engine.queue(action("s1", "p1", 0));
        engine.queue(action("s1", "p2", 1)); // epoch 1 — should not be settled now

        AtomicInteger count = new AtomicInteger(0);
        engine.settleEpoch("s1", 0, a -> count.incrementAndGet());

        assertEquals(1, count.get());
        assertEquals(1, engine.pendingActionCount("s1")); // epoch-1 action still pending
    }

    @Test
    @DisplayName("Multiple sessions are independent — settlement of one does not affect another")
    void multiSession_independence() {
        engine.initSession("s1");
        engine.initSession("s2");
        engine.queue(action("s1", "p1", 0));
        engine.queue(action("s2", "q1", 0));
        engine.queue(action("s2", "q2", 0));

        AtomicInteger s1Count = new AtomicInteger();
        AtomicInteger s2Count = new AtomicInteger();
        engine.settleEpoch("s1", 0, a -> s1Count.incrementAndGet());
        engine.settleEpoch("s2", 0, a -> s2Count.incrementAndGet());

        assertEquals(1, s1Count.get());
        assertEquals(2, s2Count.get());
    }

    // ── Thread safety ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Concurrent queue + settle — no actions lost or double-settled")
    void concurrentQueueAndSettle() throws InterruptedException {
        engine.initSession("s1");
        int threadCount = 20;
        int actionsPerThread = 50;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger settled = new AtomicInteger();

        // 20 threads each queue 50 actions at epoch 0
        for (int t = 0; t < threadCount; t++) {
            final int tid = t;
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < actionsPerThread; i++) {
                    EngineQueueResult r = engine.queue(action("s1", "p" + tid + "_" + i, 0));
                    if (r == EngineQueueResult.ACCEPTED) accepted.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        // Settle epoch 0 — all accepted actions must settle exactly once
        engine.settleEpoch("s1", 0, a -> settled.incrementAndGet());

        assertEquals(accepted.get(), settled.get(),
                "Every accepted action must settle exactly once");
        assertEquals(0, engine.pendingActionCount("s1"),
                "No pending actions must remain after settlement");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PendingAction<String> action(String sessionId, String participantId, int epoch) {
        return new PendingAction<>(sessionId, participantId, epoch, System.nanoTime(), "payload");
    }

    private void settle(String sessionId, int epoch) {
        engine.settleEpoch(sessionId, epoch, a -> {});
    }
}
