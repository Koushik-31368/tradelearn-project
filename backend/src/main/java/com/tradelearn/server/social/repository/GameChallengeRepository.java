package com.tradelearn.server.social.repository;

import com.tradelearn.server.social.model.GameChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameChallengeRepository extends JpaRepository<GameChallenge, Long> {
}
