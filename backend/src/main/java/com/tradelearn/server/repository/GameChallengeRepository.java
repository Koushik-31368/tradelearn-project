package com.tradelearn.server.repository;

import com.tradelearn.server.model.GameChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameChallengeRepository extends JpaRepository<GameChallenge, Long> {
}
