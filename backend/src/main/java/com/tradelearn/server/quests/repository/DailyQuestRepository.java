package com.tradelearn.server.quests.repository;

import com.tradelearn.server.quests.model.DailyQuest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DailyQuestRepository extends JpaRepository<DailyQuest, Long> {
    List<DailyQuest> findByIsActiveTrue();
}
