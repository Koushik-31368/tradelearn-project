package com.tradelearn.server.util;

import com.tradelearn.server.common.util.ScoringUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link ScoringUtil}.
 *
 * Covers:
 *  - Profit score: breakeven → 50, max profit → 100, max loss → 0
 *  - Risk score: zero drawdown → 100, full drawdown → 0
 *  - Accuracy score: zero trades → 0, all profitable → 100
 *  - Final composite score weights
 *  - Edge cases: zero starting balance, negative balance
 */
@DisplayName("ScoringUtil")
class ScoringUtilTest {

    private static final double TOLERANCE = 0.01;

    // ── profitScore ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("breakeven (no profit/loss) → profitScore = 50")
    void profitScore_breakeven_returns50() {
        double score = ScoringUtil.profitScore(1_000_000, 1_000_000);
        assertThat(score).isCloseTo(50.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("+100% profit → profitScore = 100")
    void profitScore_doubledBalance_returns100() {
        double score = ScoringUtil.profitScore(2_000_000, 1_000_000);
        assertThat(score).isCloseTo(100.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("-100% loss (wiped out) → profitScore = 0")
    void profitScore_wipedOut_returns0() {
        double score = ScoringUtil.profitScore(0, 1_000_000);
        assertThat(score).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("extreme profit (>100%) is clamped to 100")
    void profitScore_extremeProfit_clampsAt100() {
        double score = ScoringUtil.profitScore(10_000_000, 1_000_000);
        assertThat(score).isCloseTo(100.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("zero starting balance → profitScore = 0 (guard against division by zero)")
    void profitScore_zeroStartingBalance_returns0() {
        double score = ScoringUtil.profitScore(500_000, 0);
        assertThat(score).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("+50% profit → profitScore = 75")
    void profitScore_halfMaxProfit_returns75() {
        double score = ScoringUtil.profitScore(1_500_000, 1_000_000);
        assertThat(score).isCloseTo(75.0, within(TOLERANCE));
    }

    // ── riskScore ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("zero drawdown → riskScore = 100")
    void riskScore_noDrawdown_returns100() {
        double score = ScoringUtil.riskScore(0.0);
        assertThat(score).isCloseTo(100.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("full drawdown (100%) → riskScore = 0")
    void riskScore_fullDrawdown_returns0() {
        double score = ScoringUtil.riskScore(1.0);
        assertThat(score).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("20% drawdown → riskScore = 80")
    void riskScore_twentyPercent_returns80() {
        double score = ScoringUtil.riskScore(0.20);
        assertThat(score).isCloseTo(80.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("drawdown > 100% is clamped to 0")
    void riskScore_overOneHundredPercent_clampsAt0() {
        double score = ScoringUtil.riskScore(1.5);
        assertThat(score).isCloseTo(0.0, within(TOLERANCE));
    }

    // ── accuracyScore ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("zero trades → accuracyScore = 0")
    void accuracyScore_noTrades_returns0() {
        double score = ScoringUtil.accuracyScore(0, 0);
        assertThat(score).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("all trades profitable → accuracyScore = 100")
    void accuracyScore_allProfitable_returns100() {
        double score = ScoringUtil.accuracyScore(10, 10);
        assertThat(score).isCloseTo(100.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("half trades profitable → accuracyScore = 50")
    void accuracyScore_halfProfitable_returns50() {
        double score = ScoringUtil.accuracyScore(10, 5);
        assertThat(score).isCloseTo(50.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("no profitable trades → accuracyScore = 0")
    void accuracyScore_noProfitable_returns0() {
        double score = ScoringUtil.accuracyScore(10, 0);
        assertThat(score).isCloseTo(0.0, within(TOLERANCE));
    }

    // ── calculate (composite) ─────────────────────────────────────────────────

    @Test
    @DisplayName("perfect performance → score = 100")
    void calculate_perfectPerformance_returns100() {
        // +100% profit, 0% drawdown, 100% accuracy
        double score = ScoringUtil.calculate(2_000_000, 1_000_000, 0.0, 10, 10);
        assertThat(score).isCloseTo(100.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("worst performance → score = 0")
    void calculate_worstPerformance_returns0() {
        // total loss, full drawdown, no trades
        double score = ScoringUtil.calculate(0, 1_000_000, 1.0, 0, 0);
        assertThat(score).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("breakeven, zero drawdown, 50% accuracy → score reflects weighted average")
    void calculate_breakevenNoDrawdownHalfAccuracy_returnsExpected() {
        // profitScore=50, riskScore=100, accuracyScore=50
        // expected = 0.60*50 + 0.20*100 + 0.20*50 = 30 + 20 + 10 = 60
        double score = ScoringUtil.calculate(1_000_000, 1_000_000, 0.0, 10, 5);
        assertThat(score).isCloseTo(60.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("profit dominates score (60% weight)")
    void calculate_highProfitLowElse_profitDominates() {
        // +100% profit, 100% drawdown, 0% accuracy
        // expected = 0.60*100 + 0.20*0 + 0.20*0 = 60
        double score = ScoringUtil.calculate(2_000_000, 1_000_000, 1.0, 10, 0);
        assertThat(score).isCloseTo(60.0, within(TOLERANCE));
    }
}
