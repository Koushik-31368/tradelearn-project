package com.tradelearn.fairness.engine;

/**
 * Result of a {@link EpochLockstepEngine#queue} call.
 *
 * <p>Returned immediately, before any settlement occurs, so callers can
 * give instant feedback to participants (e.g., "action queued" vs. "rejected").
 */
public enum EngineQueueResult {

    /**
     * Action accepted and queued for end-of-epoch settlement.
     * The participant will receive a settlement notification when the epoch closes.
     */
    ACCEPTED,

    /**
     * Session ID not registered in the engine.
     * Either the session never started, or it has already been evicted.
     */
    SESSION_NOT_FOUND,

    /**
     * The action's epoch is more than one behind the current epoch for this session.
     * The epoch window has already closed; the participant should resubmit with
     * the current epoch.
     */
    STALE_EPOCH,

    /**
     * The per-session, per-epoch queue has reached its safety capacity limit.
     * This prevents unbounded memory growth under adversarial or buggy clients.
     */
    QUEUE_FULL
}
