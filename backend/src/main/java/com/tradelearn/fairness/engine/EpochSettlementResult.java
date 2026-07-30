package com.tradelearn.fairness.engine;

/**
 * Immutable summary of what happened when an epoch was settled.
 *
 * <p>Returned by {@link EpochLockstepEngine#settleEpoch} so callers can
 * log, emit metrics, or propagate results to participants.
 *
 * @param sessionId     the session that was settled
 * @param epoch         the epoch that was settled
 * @param total         total actions that were queued for this epoch
 * @param settled       actions successfully settled (settler did not throw)
 * @param rejected      actions that the settler threw an exception for
 * @param nextEpoch     the new current epoch after settlement (= epoch + 1)
 */
public record EpochSettlementResult(
        String sessionId,
        int    epoch,
        int    total,
        int    settled,
        int    rejected,
        int    nextEpoch
) {
    /** True if every queued action settled without error. */
    public boolean allSettled() {
        return rejected == 0 && total == settled;
    }

    /** True if there were no queued actions for this epoch. */
    public boolean wasEmpty() {
        return total == 0;
    }
}
