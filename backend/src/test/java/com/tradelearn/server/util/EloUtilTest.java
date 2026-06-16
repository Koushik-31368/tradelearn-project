package com.tradelearn.server.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link EloUtil}.
 *
 * Covers:
 *  - Symmetric property: equal ratings → minimal delta
 *  - Win increases rating; loss decreases rating
 *  - Draw produces near-zero delta for equal players
 *  - Rating floor enforcement (minimum = 100)
 *  - Higher-rated opponent win yields larger gain
 *  - Underdog loss yields smaller penalty
 */
@DisplayName("EloUtil")
class EloUtilTest {

    private static final int MIN_RATING = 100;

    // ── expectedScore ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("equal ratings → expected score is 0.5")
    void expectedScore_equalRatings_returnsHalf() {
        double expected = EloUtil.expectedScore(1000, 1000);
        assertThat(expected).isCloseTo(0.5, within(0.0001));
    }

    @Test
    @DisplayName("stronger player has higher expected score")
    void expectedScore_higherRatedPlayer_hasHigherExpectation() {
        double strongWin  = EloUtil.expectedScore(1200, 1000);
        double weakWin    = EloUtil.expectedScore(1000, 1200);
        assertThat(strongWin).isGreaterThan(0.5);
        assertThat(weakWin).isLessThan(0.5);
        assertThat(strongWin + weakWin).isCloseTo(1.0, within(0.0001));
    }

    // ── calculateNewRating ────────────────────────────────────────────────────

    @Test
    @DisplayName("win against equal opponent increases rating by ~16")
    void calculateNewRating_winAgainstEqual_increasesBy16() {
        int newRating = EloUtil.calculateNewRating(1000, 1000, 1.0);
        // K=32, expected=0.5 → delta = 32*(1.0-0.5) = 16
        assertThat(newRating).isEqualTo(1016);
    }

    @Test
    @DisplayName("loss against equal opponent decreases rating by ~16")
    void calculateNewRating_lossAgainstEqual_decreasesBy16() {
        int newRating = EloUtil.calculateNewRating(1000, 1000, 0.0);
        assertThat(newRating).isEqualTo(984);
    }

    @Test
    @DisplayName("draw against equal opponent → no rating change")
    void calculateNewRating_drawAgainstEqual_noChange() {
        int newRating = EloUtil.calculateNewRating(1000, 1000, 0.5);
        assertThat(newRating).isEqualTo(1000);
    }

    @Test
    @DisplayName("win against higher-rated opponent yields larger gain than against equal")
    void calculateNewRating_winAgainstStronger_largerGain() {
        int gainVsEqual    = EloUtil.calculateNewRating(1000, 1000, 1.0) - 1000;
        int gainVsStronger = EloUtil.calculateNewRating(1000, 1400, 1.0) - 1000;
        assertThat(gainVsStronger).isGreaterThan(gainVsEqual);
    }

    @Test
    @DisplayName("loss against lower-rated opponent yields larger penalty than against equal")
    void calculateNewRating_lossAgainstWeaker_largerPenalty() {
        int penaltyVsEqual  = 1000 - EloUtil.calculateNewRating(1000, 1000, 0.0);
        int penaltyVsWeaker = 1000 - EloUtil.calculateNewRating(1000, 600,  0.0);
        assertThat(penaltyVsWeaker).isGreaterThan(penaltyVsEqual);
    }

    @Test
    @DisplayName("rating cannot drop below minimum floor (100)")
    void calculateNewRating_lowRatingLoss_floorsAtMinimum() {
        // MIN_RATING player loses — should floor at 100
        int newRating = EloUtil.calculateNewRating(MIN_RATING, 2000, 0.0);
        assertThat(newRating).isEqualTo(MIN_RATING);
    }

    // ── ratingDelta ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("ratingDelta is positive for a win")
    void ratingDelta_win_positive() {
        int delta = EloUtil.ratingDelta(1000, 1000, 1.0);
        assertThat(delta).isPositive();
    }

    @Test
    @DisplayName("ratingDelta is negative for a loss")
    void ratingDelta_loss_negative() {
        int delta = EloUtil.ratingDelta(1000, 1000, 0.0);
        assertThat(delta).isNegative();
    }

    @Test
    @DisplayName("ratingDelta is zero for a draw between equal players")
    void ratingDelta_drawEqualPlayers_zero() {
        int delta = EloUtil.ratingDelta(1000, 1000, 0.5);
        assertThat(delta).isZero();
    }

    // ── Parametrized symmetry ─────────────────────────────────────────────────

    @ParameterizedTest(name = "ratings ({0}, {1}): winner gains, loser loses, sum ≈ 0")
    @CsvSource({
        "1000, 1000",
        "1200, 800",
        "800,  1200",
        "1500, 1000",
        "500,  1500"
    })
    @DisplayName("winner's gain and loser's loss sum to approximately zero (zero-sum)")
    void ratingSystem_isApproximatelyZeroSum(int playerA, int playerB) {
        int aNewRating = EloUtil.calculateNewRating(playerA, playerB, 1.0);
        int bNewRating = EloUtil.calculateNewRating(playerB, playerA, 0.0);

        int aDelta = aNewRating - playerA;
        int bDelta = bNewRating - playerB;

        // Due to floor clamping, the sum may not be exactly 0, but should be close
        assertThat(Math.abs(aDelta + bDelta)).isLessThanOrEqualTo(2);
    }
}
