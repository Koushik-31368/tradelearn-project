package com.tradelearn.server.game.epoch;

/**
 * @deprecated Replaced by {@link com.tradelearn.fairness.adapter.TradeLearnEpochAdapter},
 *             which wraps the generic {@link com.tradelearn.fairness.engine.EpochLockstepEngine}.
 *             This class is retained only as a compile-time breadcrumb; it is no longer
 *             instantiated or used by any TradeLearn service.
 *
 *             See {@code com.tradelearn.fairness} package for the extracted engine.
 */
@Deprecated(since = "epoch-lockstep-extraction", forRemoval = true)
public final class CandleEpochGate {
    private CandleEpochGate() {}
}
