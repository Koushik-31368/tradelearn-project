package com.tradelearn.server.repository;

import com.tradelearn.server.model.WeeklyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeeklyChallengeRepository extends JpaRepository<WeeklyChallenge, Long> {
    List<WeeklyChallenge> findByIsActiveTrue();
}
