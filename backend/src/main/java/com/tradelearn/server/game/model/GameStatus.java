package com.tradelearn.server.game.model;

/**
 * Type-safe enum for the lifecycle status of a {@link Game}.
 *
 * <p>Stored as its name string in the DB via {@code @Enumerated(EnumType.STRING)},
 * so the column stays human-readable and existing rows map cleanly.
 *
 * <h3>Valid transitions</h3>
 * <pre>
 *   WAITING → ACTIVE     (opponent joins)
 *   ACTIVE  → FINISHED   (time expires, normal end)
 *   ACTIVE  → ABANDONED  (player disconnect past grace period)
 *   ACTIVE  → FAILED     (afterCommit side effects unrecoverably failed)
 * </pre>
 */
public enum GameStatus {

    /** Created, waiting for a second player to join. */
    WAITING,

    /** Both players present, candle progression running. */
    ACTIVE,

    /** Game ended normally — scores and ELO applied. */
    FINISHED,

    /** A player disconnected past the reconnect grace period — winner awarded by ELO penalty. */
    ABANDONED,

    /**
     * Redis/WebSocket side-effect initialisation failed unrecoverably after the DB commit
     * (e.g. Redis down during afterCommit). Players are notified and the game is terminated
     * without scoring. Introduced by the Issue-2 reconciliation mechanism.
     */
    FAILED
}
