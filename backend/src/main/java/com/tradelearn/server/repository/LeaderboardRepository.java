package com.tradelearn.server.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tradelearn.server.model.LeaderboardEntry;

public interface LeaderboardRepository extends JpaRepository<LeaderboardEntry, Long> {

    LeaderboardEntry findByUsername(String username);

    List<LeaderboardEntry> findAllByOrderByRatingDesc();
}
