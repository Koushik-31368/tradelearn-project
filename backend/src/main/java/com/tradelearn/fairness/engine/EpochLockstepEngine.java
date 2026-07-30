package com.tradelearn.fairness.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <h1>EpochLockstepEngine — Latency-Fair Action Settlement</h1>
 *
 * <h2>Problem</h2>
 * In any real-time multiplayer simulation where participants act against a
 * shared synchronized value stream (price ticks, auction states, game epochs),
 * participants with lower network latency observe epoch transitions earlier than
 * participants with higher latency. When actions are processed on receipt, a
 * low-latency participant can act on epoch {@code N+1} data while a
 * high-latency participant is still observing epoch {@code N}. This creates
 * an informational advantage that is entirely unrelated to skill or strategy.
 *
 * <h2>Mechanism</h2>
 * The engine enforces three invariants:
 * <ol>
 *   <li><b>Epoch tagging at receipt</b> — The caller tags each
 *       {@link PendingAction} with the epoch visible to the participant at the
 *       instant the server receives the message, <em>before</em> any
 *       thread-pool submission or I/O delay. This is the epoch the participant
 *       was observing when they made their decision.</li>
 *   <li><b>Deferred queuing</b> — Actions are held in a per-session,
 *       per-epoch queue. They are not executed immediately. No action for
 *       epoch {@code N} executes while epoch {@code N} is still open.</li>
 *   <li><b>Atomic settlement before epoch advance</b> — The caller invokes
 *       {@link #settleEpoch} <em>before</em> advancing the shared state to
 *       epoch {@code N+1}. All actions queued for epoch {@code N} are settled
 *       at the epoch-{@code N} value, in server-receipt order, atomically.
 *       Only then does the caller advance the state.</li>
 * </ol>
 *
 * <h2>Fairness Guarantee</h2>
 * A participant with 10ms RTT and one with 200ms RTT submitting actions during
 * the same epoch window are settled at exactly the same shared state value.
 * The only advantage remaining is <em>which</em> action to submit — i.e., skill.
 * Network latency confers zero advantage on outcome.
 *
 * <h2>Generality</h2>
 * The engine is generic over action type {@code <A>}. It does not interpret
 * payloads, access databases, or send messages. All settlement logic is
 * delegated to the caller via {@link ActionSettler}. This makes the engine
 * applicable to any real-time multiplayer simulation: trading games, prediction
 * markets, strategy games with synchronized state, or competitive auctions.
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li><b>Multi-participant:</b> Works correctly for N ≥ 2 participants per
 *       session. Fairness guarantee holds for all N — the epoch window is the
 *       same for everyone.</li>
 *   <li><b>Very short epoch windows:</b> If the epoch duration is shorter than
 *       the maximum network RTT, some participants may never receive a
 *       notification before the epoch closes. In practice, epoch windows should
 *       be ≥ 2× the 99th-percentile RTT for the participant population.</li>
 *   <li><b>Stale-epoch tolerance:</b> The engine accepts actions up to 1 epoch
 *       behind (configurable). Actions more than {@code staleEpochTolerance}
 *       epochs behind are rejected as stale.</li>
 *   <li><b>Settlement ordering:</b> Within an epoch, actions are ordered by
 *       server receipt time ({@link PendingAction#receivedNanos()}). This is
 *       transparent and cannot be influenced by participants. However, it does
 *       mean that within the same epoch, earlier receipt still wins ties. This
 *       is an inherent property of any distributed system.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * {@link ConcurrentHashMap} for session and epoch partitions.
 * {@link CopyOnWriteArrayList} for per-epoch action queues (infrequent writes,
 * single drain-read per epoch). Settlement drains atomically via
 * {@link ConcurrentHashMap#remove(Object)} — no action can be settled twice.
 * The engine itself holds no locks; settlement is called from a single
 * scheduler thread per session in the typical deployment.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   initSession(sessionId)         — called when a session becomes active
 *   queue(action)                  — called per incoming participant action
 *   settleEpoch(sessionId, N, fn)  — called BEFORE state advances to N+1
 *   evictSession(sessionId)        — called when a session ends
 * </pre>
 *
 * @param <A> the application-specific action payload type
 */
public class EpochLockstepEngine<A> {

    private static final Logger log = LoggerFactory.getLogger(EpochLockstepEngine.class);

    /**
     * Default: accept actions up to 1 epoch behind the current epoch.
     * Actions more than this many epochs behind are rejected as stale.
     */
    private static final int DEFAULT_STALE_EPOCH_TOLERANCE = 1;

    /**
     * Default: max 1000 actions queued per session per epoch.
     * Acts as a safety valve against unbounded memory growth.
     */
    private static final int DEFAULT_MAX_QUEUE_SIZE = 1_000;

    // ── Configuration (set at construction) ────────────────────────────────

    private final int staleEpochTolerance;
    private final int maxQueueSize;

    // ── State ───────────────────────────────────────────────────────────────

    /**
     * session → (epoch → ordered list of pending actions)
     * Outer map: session lifecycle (initSession / evictSession).
     * Inner map: epoch lifecycle (created lazily, drained atomically on settle).
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, List<PendingAction<A>>>>
            sessionQueues = new ConcurrentHashMap<>();

    /**
     * Tracks the current epoch per session.
     * Updated at the end of each {@link #settleEpoch} call.
     */
    private final ConcurrentHashMap<String, Integer> currentEpoch = new ConcurrentHashMap<>();

    // ── Constructors ────────────────────────────────────────────────────────

    /** Default configuration. */
    public EpochLockstepEngine() {
        this(DEFAULT_STALE_EPOCH_TOLERANCE, DEFAULT_MAX_QUEUE_SIZE);
    }

    /**
     * Custom configuration.
     *
     * @param staleEpochTolerance actions this many epochs behind current are rejected
     * @param maxQueueSize        max pending actions per session per epoch
     */
    public EpochLockstepEngine(int staleEpochTolerance, int maxQueueSize) {
        this.staleEpochTolerance = staleEpochTolerance;
        this.maxQueueSize = maxQueueSize;
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /**
     * Register a session. Must be called before {@link #queue} or
     * {@link #settleEpoch} for this session.
     *
     * <p>Idempotent — safe to call multiple times for the same session.
     *
     * @param sessionId unique session identifier
     */
    public void initSession(String sessionId) {
        sessionQueues.putIfAbsent(sessionId, new ConcurrentHashMap<>());
        currentEpoch.put(sessionId, 0);
        log.debug("[EpochEngine] Session initialized: {}", sessionId);
    }

    /**
     * Evict a session, releasing all queued actions and epoch tracking state.
     * Should be called when the session ends (game over, connection closed).
     *
     * <p>Any actions still queued at eviction time are silently discarded.
     *
     * @param sessionId the session to evict
     */
    public void evictSession(String sessionId) {
        sessionQueues.remove(sessionId);
        currentEpoch.remove(sessionId);
        log.debug("[EpochEngine] Session evicted: {}", sessionId);
    }

    // ── Queuing ─────────────────────────────────────────────────────────────

    /**
     * Queue an action for end-of-epoch settlement.
     *
     * <p><b>Critical:</b> The caller must set {@link PendingAction#epoch()} to
     * the shared state epoch visible at the instant the server receives the
     * participant's message — before any thread-pool submission or I/O.
     * This is what makes the epoch tag meaningful.
     *
     * @param action the epoch-tagged pending action
     * @return result indicating acceptance or the rejection reason
     */
    public EngineQueueResult queue(PendingAction<A> action) {
        ConcurrentHashMap<Integer, List<PendingAction<A>>> epochMap =
                sessionQueues.get(action.sessionId());

        if (epochMap == null) {
            return EngineQueueResult.SESSION_NOT_FOUND;
        }

        // Reject actions whose epoch is too far behind the current epoch
        Integer current = currentEpoch.get(action.sessionId());
        if (current != null && action.epoch() < current - staleEpochTolerance) {
            log.debug("[EpochEngine] Stale action rejected: session={} action-epoch={} current={}",
                    action.sessionId(), action.epoch(), current);
            return EngineQueueResult.STALE_EPOCH;
        }

        // Safety valve: prevent unbounded memory growth
        List<PendingAction<A>> epochQueue = epochMap.computeIfAbsent(
                action.epoch(), k -> new CopyOnWriteArrayList<>());
        if (epochQueue.size() >= maxQueueSize) {
            log.warn("[EpochEngine] Queue full: session={} epoch={}", action.sessionId(), action.epoch());
            return EngineQueueResult.QUEUE_FULL;
        }

        epochQueue.add(action);
        log.trace("[EpochEngine] Queued: session={} participant={} epoch={}",
                action.sessionId(), action.participantId(), action.epoch());
        return EngineQueueResult.ACCEPTED;
    }

    // ── Settlement ──────────────────────────────────────────────────────────

    /**
     * Settle all actions queued for the given epoch.
     *
     * <p><b>Invariant:</b> This must be called <em>before</em> the caller
     * advances the shared state to epoch {@code epochToSettle + 1}. This
     * ensures the settler sees the epoch-{@code epochToSettle} value when
     * it executes each action.
     *
     * <p>Settlement order: by {@link PendingAction#receivedNanos()} ascending.
     * Earliest server receipt wins within the same epoch. This ordering is
     * transparent — participants cannot influence their server receipt time.
     *
     * <p>If the settler throws for a particular action, that action is counted
     * as rejected, the exception is caught, and settlement continues with the
     * next action. No action can abort the settlement of subsequent actions.
     *
     * @param sessionId      the session to settle
     * @param epochToSettle  the epoch whose queued actions are to be settled
     * @param settler        the application-specific settlement function
     * @return               a summary of what was settled, rejected, or empty
     */
    public EpochSettlementResult settleEpoch(String sessionId,
                                              int epochToSettle,
                                              ActionSettler<A> settler) {
        ConcurrentHashMap<Integer, List<PendingAction<A>>> epochMap = sessionQueues.get(sessionId);
        if (epochMap == null) {
            // Session not found — return empty result, don't throw
            log.debug("[EpochEngine] settleEpoch called for unknown session: {}", sessionId);
            return new EpochSettlementResult(sessionId, epochToSettle, 0, 0, 0, epochToSettle + 1);
        }

        // Atomically drain the epoch queue — returns null if no actions queued.
        // Using remove() means concurrent queue() calls for this epoch cannot race:
        // they'll create a fresh list that will be picked up in the next epoch.
        List<PendingAction<A>> pending = epochMap.remove(epochToSettle);

        if (pending == null || pending.isEmpty()) {
            log.debug("[EpochEngine] No actions to settle: session={} epoch={}", sessionId, epochToSettle);
            currentEpoch.put(sessionId, epochToSettle + 1);
            return new EpochSettlementResult(sessionId, epochToSettle, 0, 0, 0, epochToSettle + 1);
        }

        // Sort by server-receipt nanos — deterministic, transparent ordering
        List<PendingAction<A>> ordered = new ArrayList<>(pending);
        ordered.sort((a, b) -> Long.compare(a.receivedNanos(), b.receivedNanos()));

        log.debug("[EpochEngine] Settling {} action(s): session={} epoch={}",
                ordered.size(), sessionId, epochToSettle);

        int settled = 0;
        int rejected = 0;

        for (PendingAction<A> action : ordered) {
            try {
                settler.settle(action);
                settled++;
            } catch (Exception e) {
                rejected++;
                log.warn("[EpochEngine] Action rejected during settlement: session={} participant={}: {}",
                        sessionId, action.participantId(), e.getMessage());
            }
        }

        int nextEpoch = epochToSettle + 1;
        currentEpoch.put(sessionId, nextEpoch);

        log.debug("[EpochEngine] Settlement complete: session={} epoch={} settled={} rejected={}",
                sessionId, epochToSettle, settled, rejected);

        return new EpochSettlementResult(sessionId, epochToSettle,
                ordered.size(), settled, rejected, nextEpoch);
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    /**
     * Returns the current epoch for a session (the next epoch to be settled),
     * or {@code -1} if the session is not registered.
     */
    public int getCurrentEpoch(String sessionId) {
        Integer e = currentEpoch.get(sessionId);
        return e != null ? e : -1;
    }

    /** Total pending action count across all epochs for a session. */
    public int pendingActionCount(String sessionId) {
        ConcurrentHashMap<Integer, List<PendingAction<A>>> epochMap = sessionQueues.get(sessionId);
        if (epochMap == null) return 0;
        return epochMap.values().stream().mapToInt(List::size).sum();
    }

    /** Number of active (initialized) sessions. */
    public int activeSessionCount() {
        return sessionQueues.size();
    }

    /** True if the given session is currently registered. */
    public boolean hasSession(String sessionId) {
        return sessionQueues.containsKey(sessionId);
    }
}
