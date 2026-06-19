package com.tradelearn.server.quests.repository;

import com.tradelearn.server.quests.model.UserDailyQuest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserDailyQuestRepository extends JpaRepository<UserDailyQuest, Long> {
    List<UserDailyQuest> findByUserIdAndQuestDate(Long userId, Date questDate);
    Optional<UserDailyQuest> findByUserIdAndQuestIdAndQuestDate(Long userId, Long questId, Date questDate);
}
