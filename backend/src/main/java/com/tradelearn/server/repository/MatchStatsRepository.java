package com.tradelearn.server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tradelearn.server.model.MatchStats;

public interface MatchStatsRepository extends JpaRepository<MatchStats, Long> {

    Optional<MatchStats> findByGameIdAndUserId(Long gameId, Long userId);

    List<MatchStats> findByGameId(Long gameId);

    List<MatchStats> findByUserId(Long userId);
}
