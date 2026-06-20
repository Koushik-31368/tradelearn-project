package com.tradelearn.server.profile.service;

import com.tradelearn.server.game.model.Game;
import com.tradelearn.server.game.model.MatchStats;
import com.tradelearn.server.game.repository.GameRepository;
import com.tradelearn.server.game.repository.MatchStatsRepository;
import com.tradelearn.server.leaderboard.service.RankService;
import com.tradelearn.server.user.model.User;
import com.tradelearn.server.user.repository.UserRepository;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service responsible for assembling a user's public profile.
 *
 * <p>Previously, all of this logic was inlined inside
 * {@code LeaderboardController.getUserProfile()} as an 80+ line method body —
 * a controller-as-service anti-pattern. It now lives here so it can be
 * tested independently and reused by other surfaces (e.g. a future
 * mobile API endpoint).
 *
 * <p>Performance note: {@code computeRank()} calls
 * {@link UserRepository#findAllByOrderByRatingDesc()} which is O(n) in
 * users. This is acceptable for the current scale but should be replaced
 * with a single COUNT(*) WHERE rating &gt; :rating query once user count
 * warrants it.
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final MatchStatsRepository matchStatsRepository;
    private final RankService rankService;

    public ProfileService(UserRepository userRepository,
                          GameRepository gameRepository,
                          MatchStatsRepository matchStatsRepository,
                          RankService rankService) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.matchStatsRepository = matchStatsRepository;
        this.rankService = rankService;
    }

    // ── Public records (response shapes) ────────────────────────────────────

    public record ProfileResponse(
            Long userId,
            String username,
            int rating,
            String rankTier,
            int rank,
            int wins,
            int losses,
            int draws,
            int totalFinished,
            double avgDrawdown,
            double avgAccuracy,
            double avgScore,
            List<RecentMatch> recentMatches
    ) {}

    public record RecentMatch(
            Long gameId,
            String stockSymbol,
            String status,
            String result,
            String opponentName,
            Double finalBalance,
            Double startingBalance,
            Integer eloDelta,
            java.sql.Timestamp createdAt
    ) {}

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Build the full profile for a user, or return empty if the user is
     * not found.
     *
     * @param userId the target user's database ID
     * @return an {@link Optional} containing the profile, or empty
     */
    public Optional<ProfileResponse> getProfile(Long userId) {
        return userRepository.findById(userId)
                .map(user -> buildProfile(userId, user));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private ProfileResponse buildProfile(Long userId, User user) {
        List<Game> allGames = gameRepository.findByCreatorIdOrOpponentId(userId, userId);

        List<Game> finished = allGames.stream()
                .filter(g -> "FINISHED".equals(g.getStatus()))
                .collect(Collectors.toList());

        int wins = 0, losses = 0, draws = 0;
        for (Game g : finished) {
            if (g.getWinner() == null) {
                draws++;
            } else if (g.getWinner().getId().equals(userId)) {
                wins++;
            } else {
                losses++;
            }
        }

        List<MatchStats> stats = matchStatsRepository.findByUserId(userId);

        double avgDrawdown = stats.stream()
                .mapToDouble(MatchStats::getMaxDrawdown)
                .average().orElse(0.0);

        double avgAccuracy = stats.stream()
                .mapToDouble(s -> s.getTotalTrades() > 0
                        ? (double) s.getProfitableTrades() / s.getTotalTrades() * 100
                        : 0.0)
                .average().orElse(0.0);

        double avgScore = stats.stream()
                .mapToDouble(MatchStats::getFinalScore)
                .average().orElse(0.0);

        List<RecentMatch> recentMatches = allGames.stream()
                .sorted(Comparator.comparing(Game::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(g -> toRecentMatch(g, userId))
                .collect(Collectors.toList());

        int rank = computeRank(userId);
        String rankTier = rankService.getRankTier(user.getRating());

        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getRating(),
                rankTier,
                rank,
                wins,
                losses,
                draws,
                finished.size(),
                avgDrawdown,
                avgAccuracy,
                avgScore,
                recentMatches
        );
    }

    private RecentMatch toRecentMatch(Game g, Long userId) {
        boolean isCreator = g.getCreator() != null && g.getCreator().getId().equals(userId);

        String opponentName = isCreator
                ? (g.getOpponent() != null ? g.getOpponent().getUsername() : "—")
                : (g.getCreator() != null ? g.getCreator().getUsername() : "—");

        String result;
        if (!"FINISHED".equals(g.getStatus())) {
            result = g.getStatus();
        } else if (g.getWinner() == null) {
            result = "DRAW";
        } else if (g.getWinner().getId().equals(userId)) {
            result = "WIN";
        } else {
            result = "LOSS";
        }

        Double balance = isCreator ? g.getCreatorFinalBalance() : g.getOpponentFinalBalance();
        Integer eloDelta = isCreator ? g.getCreatorRatingDelta() : g.getOpponentRatingDelta();

        return new RecentMatch(
                g.getId(),
                g.getStockSymbol(),
                g.getStatus(),
                result,
                opponentName,
                balance,
                g.getStartingBalance(),
                eloDelta,
                g.getCreatedAt()
        );
    }

    /**
     * Compute the 1-based global rank of a user by counting how many users
     * have a higher rating.
     *
     * <p>TODO: Replace with a single SQL COUNT(*) query for O(1) performance
     * once the user base warrants it.
     */
    @SuppressWarnings("null")
    private int computeRank(Long userId) {
        List<User> top = userRepository.findAllByOrderByRatingDesc();
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).getId().equals(userId)) {
                return i + 1;
            }
        }
        return 0;
    }
}
