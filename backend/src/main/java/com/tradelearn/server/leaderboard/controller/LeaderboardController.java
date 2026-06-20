package com.tradelearn.server.leaderboard.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.dto.LeaderboardDTO;
import com.tradelearn.server.user.model.User;
import com.tradelearn.server.user.repository.UserRepository;
import com.tradelearn.server.leaderboard.service.RankService;

/**
 * Exposes ranked leaderboard data at {@code /api/users/leaderboard}.
 *
 * <p>Profile-specific data (win/loss record, match history, per-game stats)
 * has been extracted to {@link com.tradelearn.server.profile.controller.ProfileController}
 * at {@code /api/profile/{userId}}.
 */
@RestController
@RequestMapping("/api/users")
public class LeaderboardController {

    private final UserRepository userRepository;
    private final RankService rankService;

    public LeaderboardController(UserRepository userRepository,
                                 RankService rankService) {
        this.userRepository = userRepository;
        this.rankService = rankService;
    }

    // ================= LEADERBOARD =================

    /** Default leaderboard endpoint — used by the frontend LeaderboardPage. */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardDTO>> getLeaderboard() {
        List<User> allUsers = userRepository.findAllByOrderByRatingDesc();
        List<LeaderboardDTO> dtos = allUsers.stream()
                .map(u -> new LeaderboardDTO(u, rankService.getRankTier(u.getRating())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/leaderboard/top10")
    public ResponseEntity<List<LeaderboardDTO>> getTop10Leaderboard() {
        List<User> topUsers = userRepository.findTop10ByOrderByRatingDesc();
        List<LeaderboardDTO> dtos = topUsers.stream()
                .map(u -> new LeaderboardDTO(u, rankService.getRankTier(u.getRating())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/leaderboard/tier/{tierName}")
    public ResponseEntity<List<LeaderboardDTO>> getLeaderboardByTier(@PathVariable String tierName) {
        List<User> allUsers = userRepository.findAllByOrderByRatingDesc();
        List<LeaderboardDTO> dtos = allUsers.stream()
                .map(u -> new LeaderboardDTO(u, rankService.getRankTier(u.getRating())))
                .filter(dto -> dto.getRank().equalsIgnoreCase(tierName))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
