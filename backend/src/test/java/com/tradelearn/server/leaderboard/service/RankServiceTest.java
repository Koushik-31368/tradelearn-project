package com.tradelearn.server.leaderboard.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link RankService}.
 *
 * <p>Tests every tier boundary: Bronze, Silver, Gold, Platinum, Diamond,
 * Master, and Grandmaster. Also validates boundary conditions (tier edges)
 * to guard against off-by-one regressions.
 */
class RankServiceTest {

    private final RankService rankService = new RankService();

    @ParameterizedTest(name = "rating={0} → {1}")
    @CsvSource({
        "0,        Bronze",
        "499,      Bronze",
        "500,      Silver",
        "799,      Silver",
        "800,      Gold",
        "1099,     Gold",
        "1100,     Platinum",
        "1499,     Platinum",
        "1500,     Diamond",
        "1999,     Diamond",
        "2000,     Master",
        "2499,     Master",
        "2500,     Grandmaster",
        "9999,     Grandmaster"
    })
    void getRankTier_returnsCorrectTier(int rating, String expectedTier) {
        assertThat(rankService.getRankTier(rating)).isEqualTo(expectedTier);
    }

    @Test
    void getRankTier_exactBronzeSilverBoundary() {
        assertThat(rankService.getRankTier(499)).isEqualTo("Bronze");
        assertThat(rankService.getRankTier(500)).isEqualTo("Silver");
    }

    @Test
    void getRankTier_exactSilverGoldBoundary() {
        assertThat(rankService.getRankTier(799)).isEqualTo("Silver");
        assertThat(rankService.getRankTier(800)).isEqualTo("Gold");
    }

    @Test
    void getRankTier_exactGoldPlatinumBoundary() {
        assertThat(rankService.getRankTier(1099)).isEqualTo("Gold");
        assertThat(rankService.getRankTier(1100)).isEqualTo("Platinum");
    }

    @Test
    void getRankTier_exactPlatinumDiamondBoundary() {
        assertThat(rankService.getRankTier(1499)).isEqualTo("Platinum");
        assertThat(rankService.getRankTier(1500)).isEqualTo("Diamond");
    }

    @Test
    void getRankTier_exactDiamondMasterBoundary() {
        assertThat(rankService.getRankTier(1999)).isEqualTo("Diamond");
        assertThat(rankService.getRankTier(2000)).isEqualTo("Master");
    }

    @Test
    void getRankTier_exactMasterGrandmasterBoundary() {
        assertThat(rankService.getRankTier(2499)).isEqualTo("Master");
        assertThat(rankService.getRankTier(2500)).isEqualTo("Grandmaster");
    }
}
