package com.tradelearn.server.repository;

import com.tradelearn.server.model.UserWeeklyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserWeeklyChallengeRepository extends JpaRepository<UserWeeklyChallenge, Long> {
    List<UserWeeklyChallenge> findByUserIdAndWeekStartDate(Long userId, Date weekStartDate);
    Optional<UserWeeklyChallenge> findByUserIdAndChallengeIdAndWeekStartDate(Long userId, Long challengeId, Date weekStartDate);
}
