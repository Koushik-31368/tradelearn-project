package com.tradelearn.server.util;

/**
 * Standard ELO rating calculator for 1v1 matches.
 *
 * K-factor is 32 (standard for casual competitive play).
 *
 * Expected score  = 1 / (1 + 10^((opponentRating − playerRating) / 400))
 * New rating      = oldRating + K × (actualScore − expectedScore)
 *
 * actualScore: 1.0 = win, 0.5 = draw, 0.0 = loss
 */
public final class EloUtil {

    /** K-factor: how much a single match can swing the rating */
    private static final double K = 32.0;

    /** Minimum rating floor — players cannot drop below this */
    private static final int MIN_RATING = 100;

    private EloUtil() {}

    /**
     * Calculate the new ELO rating for a player.
     *
     * @param playerRating   current rating of the player
     * @param opponentRating current rating of the opponent
     * @param actualScore    1.0 = win, 0.5 = draw, 0.0 = loss
     * @return new rating (floored at {@link #MIN_RATING})
     */
    public static int calculateNewRating(int playerRating, int opponentRating, double actualScore) {
        double expected = expectedScore(playerRating, opponentRating);
        int newRating = (int) Math.round(playerRating + K * (actualScore - expected));
        return Math.max(MIN_RATING, newRating);
    }

    /**
     * Calculate the rating change (delta) for a player.
     * Positive = gained rating, negative = lost rating.
     */
    public static int ratingDelta(int playerRating, int opponentRating, double actualScore) {
        return calculateNewRating(playerRating, opponentRating, actualScore) - playerRating;
    }

    /**
     * Expected score: probability of winning based on rating difference.
     * Returns a value between 0.0 and 1.0.
     */
    public static double expectedScore(int playerRating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - playerRating) / 400.0));
    }
}
