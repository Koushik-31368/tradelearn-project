package com.tradelearn.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tradelearn.server.model.TradeJournal;
import java.util.List;

@Repository
public interface TradeJournalRepository extends JpaRepository<TradeJournal, Long> {
    List<TradeJournal> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<TradeJournal> findByUserIdAndReflectionStatus(Long userId, String reflectionStatus);
}
