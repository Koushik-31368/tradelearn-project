package com.tradelearn.server.controller;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.model.LeaderboardEntry;
import com.tradelearn.server.repository.LeaderboardRepository;
import com.tradelearn.server.service.LeaderboardService;

/**
 * REST controller for the Practice Mode ELO leaderboard.
 * Base path: /api/leaderboard
 *
 * This is entirely separate from the multiplayer leaderboard
 * served by LeaderboardController at /api/users/leaderboard.
 */
@RestController
@RequestMapping("/api/leaderboard")
public class PracticeLeaderboardController {

    private final LeaderboardRepository leaderboardRepository;
    private final LeaderboardService leaderboardService;

    public PracticeLeaderboardController(LeaderboardRepository leaderboardRepository,
                                          LeaderboardService leaderboardService) {
        this.leaderboardRepository = leaderboardRepository;
        this.leaderboardService = leaderboardService;
    }

    // ── GET /api/leaderboard ─────────────────────────────────────────────────
    /** Returns all practice leaderboard entries sorted by rating descending. */
    @GetMapping
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard() {
        List<LeaderboardEntry> entries =
                leaderboardRepository.findAll(Sort.by(Sort.Direction.DESC, "rating"));
        return ResponseEntity.ok(entries);
    }

    // ── POST /api/leaderboard/update ─────────────────────────────────────────
    /**
     * Update a player's practice ELO rating.
     *
     * @param username   the player's username
     * @param scoreDelta positive for a correct decision, negative for wrong
     */
    @PostMapping("/update")
    public ResponseEntity<LeaderboardEntry> updateRating(
            @RequestParam String username,
            @RequestParam int scoreDelta) {

        leaderboardService.updateRating(username, scoreDelta);
        LeaderboardEntry updated = leaderboardService.getEntry(username);
        return ResponseEntity.ok(updated);
    }

    // ── GET /api/leaderboard/{username} ─────────────────────────────────────
    /** Returns the practice entry for a specific player. */
    @GetMapping("/{username}")
    public ResponseEntity<LeaderboardEntry> getEntry(@PathVariable String username) {
        LeaderboardEntry entry = leaderboardService.getEntry(username);
        if (entry == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(entry);
    }
}
