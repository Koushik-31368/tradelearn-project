
package com.tradelearn.server.controller;


import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.dto.LeaderboardDTO;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.MatchStats;
import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.repository.MatchStatsRepository;
import com.tradelearn.server.repository.UserRepository;
import com.tradelearn.server.service.RankService;

@RestController
@RequestMapping("/api/users")
public class LeaderboardController {

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final MatchStatsRepository matchStatsRepository;
    private final RankService rankService;

    public LeaderboardController(UserRepository userRepository,
				 GameRepository gameRepository,
				 MatchStatsRepository matchStatsRepository,
				 RankService rankService) {
	this.userRepository = userRepository;
	this.gameRepository = gameRepository;
	this.matchStatsRepository = matchStatsRepository;
	this.rankService = rankService;
    }

    // ================= LEADERBOARD =================

    @GetMapping("/leaderboard/top10")
    public ResponseEntity<List<LeaderboardDTO>> getTop10Leaderboard() {
	List<User> topUsers = userRepository.findTop10ByOrderByRatingDesc();

	List<LeaderboardDTO> dtos = topUsers.stream()
		.map(u -> new LeaderboardDTO(u, rankService.getRankTier(u.getRating())))
		.collect(Collectors.toList());

	return ResponseEntity.ok(dtos);
    }

    @GetMapping("/leaderboard/all")
    public ResponseEntity<List<LeaderboardDTO>> getAllLeaderboard() {
	List<User> allUsers = userRepository.findAllByOrderByRatingDesc();

	List<LeaderboardDTO> dtos = allUsers.stream()
		.map(u -> new LeaderboardDTO(u, rankService.getRankTier(u.getRating())))
		.collect(Collectors.toList());

	return ResponseEntity.ok(dtos);
    }

    // ================= PROFILE =================

    @GetMapping("/{userId}/profile")
    public ResponseEntity<?> getUserProfile(@PathVariable Long userId) {

	return userRepository.findById(userId)
		.map(user -> {

		    List<Game> allGames = gameRepository.findByCreatorIdOrOpponentId(userId, userId);

		    List<Game> finished = allGames.stream()
			    .filter(g -> "FINISHED".equals(g.getStatus()))
			    .collect(Collectors.toList());

		    int wins = 0, losses = 0, draws = 0;

		    for (Game g : finished) {
			if (g.getWinner() == null) draws++;
			else if (g.getWinner().getId().equals(userId)) wins++;
			else losses++;
		    }

		    List<MatchStats> stats = matchStatsRepository.findByUserId(userId);

		    double avgDrawdown = stats.stream()
			    .mapToDouble(MatchStats::getMaxDrawdown)
			    .average().orElse(0);

		    double avgAccuracy = stats.stream()
			    .mapToDouble(s -> s.getTotalTrades() > 0
				    ? (double) s.getProfitableTrades() / s.getTotalTrades() * 100
				    : 0)
			    .average().orElse(0);

		    double avgScore = stats.stream()
			    .mapToDouble(MatchStats::getFinalScore)
			    .average().orElse(0);

		    List<Game> recent = allGames.stream()
			    .sorted(Comparator.comparing(Game::getCreatedAt,
				    Comparator.nullsLast(Comparator.reverseOrder())))
			    .limit(10)
			    .collect(Collectors.toList());

		    List<RecentMatch> recentMatches = recent.stream().map(g -> {

			boolean isCreator = g.getCreator() != null &&
				g.getCreator().getId().equals(userId);

			String opponentName = isCreator
				? (g.getOpponent() != null ? g.getOpponent().getUsername() : "—")
				: (g.getCreator() != null ? g.getCreator().getUsername() : "—");

			String result;
			if (!"FINISHED".equals(g.getStatus())) result = g.getStatus();
			else if (g.getWinner() == null) result = "DRAW";
			else if (g.getWinner().getId().equals(userId)) result = "WIN";
			else result = "LOSS";

			Double balance = isCreator ?
				g.getCreatorFinalBalance() :
				g.getOpponentFinalBalance();

			Integer eloDelta = isCreator ?
				g.getCreatorRatingDelta() :
				g.getOpponentRatingDelta();

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

		    }).collect(Collectors.toList());

		    List<User> top = userRepository.findAllByOrderByRatingDesc();
		    int rank = 0;
		    for (int i = 0; i < top.size(); i++) {
			if (top.get(i).getId().equals(userId)) {
			    rank = i + 1;
			    break;
			}
		    }

		    ProfileResponse profile = new ProfileResponse(
			    user.getId(),
			    user.getUsername(),
			    user.getRating(),
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

		    return ResponseEntity.ok(profile);
		})
		.orElse(ResponseEntity.notFound().build());
    }

    // ================= RECORDS =================

	public record ProfileResponse(
		Long userId,
		String username,
		int rating,
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
}

	record RecentMatch(
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
