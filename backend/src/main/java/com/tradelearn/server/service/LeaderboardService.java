package com.tradelearn.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradelearn.server.model.LeaderboardEntry;
import com.tradelearn.server.repository.LeaderboardRepository;

/**
 * Manages Practice Mode ELO ratings stored in the practice_leaderboard table.
 * Completely independent from the multiplayer rating stored on the User entity.
 */
@Service
public class LeaderboardService {

    @Autowired
    private LeaderboardRepository repository;

    /**
     * Adjusts a player's practice ELO by {@code scoreDelta} points.
     * Creates a new entry (starting at 1000) if the player has no record yet.
     * Rating is clamped to a minimum of 0.
     *
     * @param username   the player's username
     * @param scoreDelta positive (correct decision) or negative (incorrect decision)
     */
    @Transactional
    public void updateRating(String username, int scoreDelta) {
        LeaderboardEntry entry = repository.findByUsername(username);

        if (entry == null) {
            entry = new LeaderboardEntry();
            entry.setUsername(username);
            entry.setRating(1000);
        }

        int newRating = Math.max(0, entry.getRating() + scoreDelta);
        entry.setRating(newRating);
        entry.setGamesPlayed(entry.getGamesPlayed() + 1);

        repository.save(entry);
    }

    /**
     * Retrieves the practice leaderboard entry for a given username,
     * or {@code null} if the player has not yet participated.
     */
    public LeaderboardEntry getEntry(String username) {
        return repository.findByUsername(username);
    }
}
