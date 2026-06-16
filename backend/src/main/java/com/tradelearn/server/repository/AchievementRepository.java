package com.tradelearn.server.repository;

import com.tradelearn.server.model.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {
    Optional<Achievement> findByName(String name);
}
