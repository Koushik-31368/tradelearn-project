package com.tradelearn.server.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.repository.MatchStatsRepository;
import com.tradelearn.server.repository.UserRepository;

@RestController
@RequestMapping("/api/users")
public class LeaderboardController {

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final MatchStatsRepository matchStatsRepository;

    public LeaderboardController(UserRepository userRepository,
                                 GameRepository gameRepository,
                                 MatchStatsRepository matchStatsRepository) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.matchStatsRepository = matchStatsRepository;
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard() {
        List<User> topUsers = userRepository.findTop50ByOrderByRatingDesc();
        List<LeaderboardEntry> entries = new ArrayList<>();

        int rank = 1;
        for (User u : topUsers) {
            long totalMatches = gameRepository.countByCreatorIdOrOpponentId(u.getId(), u.getId());
            entries.add(new LeaderboardEntry(rank++, u.getId(), u.getUsername(), u.getRating(), totalMatches));
        }

        return ResponseEntity.ok(entries);
    }

    // ── Inline DTO ──
    public record LeaderboardEntry(int rank, Long userId, String username, int rating, long totalMatches) {}

    // ==================== PROFILE ====================

    /**
     * GET /api/users/{userId}/profile
     * Aggregated stats: rating, wins, losses, avg drawdown, avg accuracy, recent 10 matches.
     */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<?> getUserProfile(@PathVariable Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    // All games this user participated in
                    List<Game> allGames = gameRepository.findByCreatorIdOrOpponentId(userId, userId);

                    // Only finished games
                    List<Game> finished = allGames.stream()
                            .filter(g -> "FINISHED".equals(g.getStatus()))
                            .collect(Collectors.toList());

                    int wins = 0;
                    int losses = 0;
                    int draws = 0;
                    for (Game g : finished) {
                        if (g.getWinner() == null) {
                            draws++;
                        } else if (g.getWinner().getId().equals(userId)) {
                            wins++;
                        } else {
                            losses++;
                        }
                    }

                    // Aggregate stats from MatchStats
                    List<MatchStats> allStats = matchStatsRepository.findByUserId(userId);
                    double avgDrawdown = 0;
                    double avgAccuracy = 0;
                    double avgScore = 0;
                    if (!allStats.isEmpty()) {
                        avgDrawdown = allStats.stream()
                                .mapToDouble(MatchStats::getMaxDrawdown)
                                .average().orElse(0);
                        avgAccuracy = allStats.stream()
                                .mapToDouble(s -> s.getTotalTrades() > 0
                                        ? (double) s.getProfitableTrades() / s.getTotalTrades() * 100
                                        : 0)
                                .average().orElse(0);
                        avgScore = allStats.stream()
                                .mapToDouble(MatchStats::getFinalScore)
                                .average().orElse(0);
                    }

                    // Recent 10 matches (newest first)
                    List<Game> recent = allGames.stream()
                            .sorted(Comparator.comparing(Game::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                            .limit(10)
                            .collect(Collectors.toList());

                    List<RecentMatch> recentMatches = recent.stream().map(g -> {
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
                        Double myBalance = isCreator ? g.getCreatorFinalBalance() : g.getOpponentFinalBalance();
                        Integer myEloDelta = isCreator ? g.getCreatorRatingDelta() : g.getOpponentRatingDelta();

                        return new RecentMatch(
                                g.getId(), g.getStockSymbol(), g.getStatus(),
                                result, opponentName, myBalance,
                                g.getStartingBalance(), myEloDelta, g.getCreatedAt()
                        );
                    }).collect(Collectors.toList());

                    // Leaderboard rank
                    List<User> top = userRepository.findTop50ByOrderByRatingDesc();
                    int rank = 0;
                    for (int i = 0; i < top.size(); i++) {
                        if (top.get(i).getId().equals(userId)) { rank = i + 1; break; }
                    }

                    ProfileResponse profile = new ProfileResponse(
                            user.getId(), user.getUsername(), user.getRating(), rank,
                            wins, losses, draws,
                            finished.size(),
                            avgDrawdown, avgAccuracy, avgScore,
                            recentMatches
                    );
                    return ResponseEntity.ok(profile);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    public record ProfileResponse(
            Long userId, String username, int rating, int rank,
            int wins, int losses, int draws, int totalFinished,
            double avgDrawdown, double avgAccuracy, double avgScore,
            List<RecentMatch> recentMatches
    ) {}

    public record RecentMatch(
            Long gameId, String stockSymbol, String status,
            String result, String opponentName, Double finalBalance,
            Double startingBalance, Integer eloDelta,
            java.sql.Timestamp createdAt
    ) {}
}
