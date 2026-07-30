package com.tradelearn.fairness.engine;

/**
 * A participant action tagged with the epoch at which the server received it.
 *
 * <h3>Why epoch tagging is the critical invariant</h3>
 * In any multiplayer simulation where participants act against a shared
 * synchronized value stream (candle prices, auction ticks, game state),
 * participants with lower network latency observe epoch transitions earlier.
 * Without epoch tagging, their actions are processed with access to
 * information that higher-latency participants have not yet received.
 *
 * <p>By recording {@link #epoch} at the instant the server receives the
 * message — before any thread-pool submission or I/O — the engine can
 * assign each action to the correct epoch window regardless of when it
 * is eventually executed. This is the foundation of the fairness guarantee.
 *
 * <h3>Generic usage</h3>
 * The type parameter {@code A} represents the application-specific action
 * payload (e.g., a trade order, a bid, a game move). The engine itself
 * does not interpret the payload; settlement is delegated to the caller
 * via {@link ActionSettler}.
 *
 * @param <A>            application-specific action type
 * @param sessionId      identifies the multiplayer session (game, auction room, etc.)
 * @param participantId  identifies the actor within the session
 * @param epoch          shared state version at server-receipt time
 * @param receivedNanos  {@link System#nanoTime()} at server-receipt (ordering within epoch)
 * @param payload        the application-specific action to settle
 */
public record PendingAction<A>(
        String sessionId,
        String participantId,
        int    epoch,
        long   receivedNanos,
        A      payload
) {}
