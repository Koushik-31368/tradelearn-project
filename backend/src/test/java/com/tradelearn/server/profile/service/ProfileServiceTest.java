package com.tradelearn.server.profile.service;

import com.tradelearn.server.game.model.Game;
import com.tradelearn.server.game.model.MatchStats;
import com.tradelearn.server.game.repository.GameRepository;
import com.tradelearn.server.game.repository.MatchStatsRepository;
import com.tradelearn.server.leaderboard.service.RankService;
import com.tradelearn.server.user.model.User;
import com.tradelearn.server.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProfileService}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>User not found → empty Optional</li>
 *   <li>Win/loss/draw counting from finished games</li>
 *   <li>Global rank calculation via {@code countByRatingGreaterThan}</li>
 *   <li>Average drawdown / accuracy / score aggregation</li>
 *   <li>Recent matches ordered by createdAt descending, capped at 10</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private GameRepository gameRepository;
    @Mock private MatchStatsRepository matchStatsRepository;
    @Mock private RankService rankService;

    @InjectMocks
    private ProfileService profileService;

    private User user;
    private User opponent;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("trader1");
        user.setRating(1500);

        opponent = new User();
        opponent.setId(2L);
        opponent.setUsername("trader2");
        opponent.setRating(1200);
    }

    // ── Not found ────────────────────────────────────────────────────────────

    @Test
    void getProfile_userNotFound_returnsEmpty() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<ProfileService.ProfileResponse> result = profileService.getProfile(99L);

        assertThat(result).isEmpty();
        verifyNoInteractions(gameRepository);
    }

    // ── Win/loss/draw counting ────────────────────────────────────────────────

    @Test
    void getProfile_correctlyCountsWinsLossesDraws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(rankService.getRankTier(1500)).thenReturn("Diamond");
        // computeRank: user (1500) has 0 users rated higher → rank 1
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.countByRatingGreaterThan(1500)).thenReturn(0L);

        Game win  = finishedGame(user);   // user is winner
        Game loss = finishedGame(opponent); // opponent is winner
        Game draw = finishedGame(null);   // no winner

        when(gameRepository.findByCreatorIdOrOpponentId(1L, 1L)).thenReturn(List.of(win, loss, draw));
        when(matchStatsRepository.findByUserId(1L)).thenReturn(List.of());

        ProfileService.ProfileResponse profile = profileService.getProfile(1L).orElseThrow();

        assertThat(profile.wins()).isEqualTo(1);
        assertThat(profile.losses()).isEqualTo(1);
        assertThat(profile.draws()).isEqualTo(1);
        assertThat(profile.totalFinished()).isEqualTo(3);
    }

    // ── Rank calculation ──────────────────────────────────────────────────────

    @Test
    void getProfile_correctlyComputesGlobalRank() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(rankService.getRankTier(1500)).thenReturn("Diamond");
        // 1 user has a higher rating than user (1500), so rank = 1 + 1 = 2
        when(userRepository.countByRatingGreaterThan(1500)).thenReturn(1L);
        when(gameRepository.findByCreatorIdOrOpponentId(1L, 1L)).thenReturn(List.of());
        when(matchStatsRepository.findByUserId(1L)).thenReturn(List.of());

        ProfileService.ProfileResponse profile = profileService.getProfile(1L).orElseThrow();

        assertThat(profile.rank()).isEqualTo(2);
        assertThat(profile.rankTier()).isEqualTo("Diamond");
    }

    // ── Stats aggregation ──────────────────────────────────────────────────────

    @Test
    void getProfile_aggregatesMatchStatsCorrectly() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(rankService.getRankTier(1500)).thenReturn("Diamond");
        when(userRepository.countByRatingGreaterThan(1500)).thenReturn(0L);
        when(gameRepository.findByCreatorIdOrOpponentId(1L, 1L)).thenReturn(List.of());

        MatchStats stats1 = new MatchStats();
        stats1.setMaxDrawdown(5.0);
        stats1.setTotalTrades(10);
        stats1.setProfitableTrades(7); // 70% accuracy
        stats1.setFinalScore(80.0);

        MatchStats stats2 = new MatchStats();
        stats2.setMaxDrawdown(3.0);
        stats2.setTotalTrades(20);
        stats2.setProfitableTrades(10); // 50% accuracy
        stats2.setFinalScore(60.0);

        when(matchStatsRepository.findByUserId(1L)).thenReturn(List.of(stats1, stats2));

        ProfileService.ProfileResponse profile = profileService.getProfile(1L).orElseThrow();

        assertThat(profile.avgDrawdown()).isEqualTo(4.0);      // (5 + 3) / 2
        assertThat(profile.avgAccuracy()).isEqualTo(60.0);     // (70 + 50) / 2
        assertThat(profile.avgScore()).isEqualTo(70.0);        // (80 + 60) / 2
    }

    // ── Recent matches capped at 10 ──────────────────────────────────────────

    @Test
    void getProfile_recentMatchesCappedAt10() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(rankService.getRankTier(1500)).thenReturn("Diamond");
        when(userRepository.countByRatingGreaterThan(1500)).thenReturn(0L);
        when(matchStatsRepository.findByUserId(1L)).thenReturn(List.of());

        // 15 finished games
        List<Game> games = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            games.add(finishedGame(user));
        }
        when(gameRepository.findByCreatorIdOrOpponentId(1L, 1L)).thenReturn(games);

        ProfileService.ProfileResponse profile = profileService.getProfile(1L).orElseThrow();

        assertThat(profile.recentMatches()).hasSize(10);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Game finishedGame(User winner) {
        Game game = new Game();
        game.setId((long) (Math.random() * 10_000));
        game.setCreator(user);
        game.setOpponent(opponent);
        game.setStockSymbol("TCS");
        game.setStartingBalance(1_000_000.0);
        game.setStatus("FINISHED");
        game.setWinner(winner);
        return game;
    }
}
