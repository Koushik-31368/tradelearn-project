package com.tradelearn.server.util;

/**
 * Hybrid scoring system for 1v1 matches.
 *
 * Final score = 60% Profit Score + 20% Risk Score + 20% Accuracy Score
 *
 * Profit Score   = profitPct clamped to [−100, +100], mapped to 0–100.
 * Risk Score     = (1 − maxDrawdown) × 100. Lower drawdown → higher score.
 * Accuracy Score = (profitableTrades / totalTrades) × 100. 0 trades → 0.
 *
 * All component scores are on a 0–100 scale.
 * Final score is also 0–100.
 */
public final class ScoringUtil {

    private static final double PROFIT_WEIGHT   = 0.60;
    private static final double RISK_WEIGHT     = 0.20;
    private static final double ACCURACY_WEIGHT = 0.20;

    /** Maximum profit percentage considered for scoring (caps outliers) */
    private static final double MAX_PROFIT_PCT = 100.0;

    private ScoringUtil() {}

    // ==================== MAIN ENTRY POINT ====================

    /**
     * Calculate the composite match score for a player.
     *
     * @param finalBalance      player's equity at match end
     * @param startingBalance   the initial balance of the match
     * @param maxDrawdown       largest percentage drop from peak (0.0 – 1.0)
     * @param totalTrades       total trades placed
     * @param profitableTrades  trades that were profitable
     * @return score on a 0–100 scale
     */
    public static double calculate(double finalBalance, double startingBalance,
                                   double maxDrawdown,
                                   int totalTrades, int profitableTrades) {

        double profitScore   = profitScore(finalBalance, startingBalance);
        double riskScore     = riskScore(maxDrawdown);
        double accuracyScore = accuracyScore(totalTrades, profitableTrades);

        return (PROFIT_WEIGHT   * profitScore)
             + (RISK_WEIGHT     * riskScore)
             + (ACCURACY_WEIGHT * accuracyScore);
    }

    // ==================== COMPONENT SCORES ====================

    /**
     * Maps profit percentage to a 0–100 score.
     * −100% → 0, 0% → 50, +100% → 100.
     * Clamped so extreme outliers don't distort the curve.
     */
    static double profitScore(double finalBalance, double startingBalance) {
        if (startingBalance <= 0) return 0;

        double profitPct = ((finalBalance - startingBalance) / startingBalance) * 100.0;

        // Clamp to [−MAX_PROFIT_PCT, +MAX_PROFIT_PCT]
        profitPct = Math.max(-MAX_PROFIT_PCT, Math.min(MAX_PROFIT_PCT, profitPct));

        // Linear map: −100 → 0, 0 → 50, +100 → 100
        return (profitPct + MAX_PROFIT_PCT) / (2.0 * MAX_PROFIT_PCT) * 100.0;
    }

    /**
     * Lower drawdown = better risk management = higher score.
     * maxDrawdown is 0.0–1.0 (percentage).
     * 0% drawdown → 100, 100% drawdown → 0.
     */
    static double riskScore(double maxDrawdown) {
        double clamped = Math.max(0.0, Math.min(1.0, maxDrawdown));
        return (1.0 - clamped) * 100.0;
    }

    /**
     * Trade accuracy: profitable / total × 100.
     * 0 trades → 0 score (no activity is not rewarded).
     */
    static double accuracyScore(int totalTrades, int profitableTrades) {
        if (totalTrades <= 0) return 0;
        return ((double) profitableTrades / totalTrades) * 100.0;
    }
}
