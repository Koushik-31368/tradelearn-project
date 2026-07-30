package com.tradelearn.fairness.engine;

/**
 * Callback interface invoked by {@link EpochLockstepEngine} during epoch settlement.
 *
 * <p>The engine does not interpret action payloads. Instead, it delegates
 * execution to the application layer via this interface. This keeps the
 * engine free of application-specific dependencies (databases, message
 * brokers, domain models).
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Called once per action, in server-receipt order within the epoch.</li>
 *   <li>Called synchronously in the thread that invoked
 *       {@link EpochLockstepEngine#settleEpoch}.</li>
 *   <li>Exceptions thrown by the settler are caught by the engine, counted
 *       as {@link EpochSettlementResult#rejected()}, and do not abort
 *       settlement of subsequent actions.</li>
 * </ul>
 *
 * @param <A> application-specific action type
 */
@FunctionalInterface
public interface ActionSettler<A> {

    /**
     * Settle a single action.
     *
     * @param action the pending action (contains session, participant, epoch, payload)
     * @throws Exception if the action cannot be settled (e.g., validation failure,
     *                   insufficient funds, illegal move). The engine will count this
     *                   as a rejection and continue with the next action.
     */
    void settle(PendingAction<A> action) throws Exception;
}
