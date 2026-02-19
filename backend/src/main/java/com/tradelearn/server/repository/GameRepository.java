package com.tradelearn.server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tradelearn.server.model.Game;

import jakarta.persistence.LockModeType;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByStatus(String status);

    List<Game> findByCreatorIdOrOpponentId(Long creatorId, Long opponentId);

    List<Game> findByCreatorIdOrOpponentIdAndStatus(Long creatorId, Long opponentId, String status);

    long countByCreatorIdOrOpponentId(Long creatorId, Long opponentId);

    // ── PESSIMISTIC WRITE LOCK ──────────────────────────────
    // SELECT ... FOR UPDATE — blocks concurrent readers/writers
    // until the owning transaction commits. Used for all critical
    // state mutations: join, end, candle advance.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Game g WHERE g.id = :id")
    Optional<Game> findByIdForUpdate(@Param("id") Long id);

    // ── ATOMIC JOIN (CAS-style) ─────────────────────────────
    // Single UPDATE that only succeeds if status is still WAITING.
    // Returns the count of affected rows (0 = someone else joined first).
    @Modifying
    @Query("UPDATE Game g SET g.status = 'ACTIVE', g.opponent = :opponent, " +
           "g.startTime = CURRENT_TIMESTAMP " +
           "WHERE g.id = :gameId AND g.status = 'WAITING'")
    int atomicJoin(@Param("gameId") Long gameId,
                   @Param("opponent") com.tradelearn.server.model.User opponent);
}