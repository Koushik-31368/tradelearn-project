package com.tradelearn.server.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tradelearn.server.model.Game;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByStatus(String status);

    List<Game> findByCreatorIdOrOpponentId(Long creatorId, Long opponentId);

    List<Game> findByCreatorIdOrOpponentIdAndStatus(Long creatorId, Long opponentId, String status);

    long countByCreatorIdOrOpponentId(Long creatorId, Long opponentId);
}