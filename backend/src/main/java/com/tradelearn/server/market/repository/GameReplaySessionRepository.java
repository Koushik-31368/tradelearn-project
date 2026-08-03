package com.tradelearn.server.market.repository;

import com.tradelearn.server.market.model.GameReplaySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameReplaySessionRepository extends JpaRepository<GameReplaySession, Long> {

    /**
     * Looks up the replay session for a given game.
     * Returns {@link Optional#empty()} if the game uses classpath JSON
     * (demo/test mode with no DB candle data configured).
     */
    Optional<GameReplaySession> findByGameId(Long gameId);

    boolean existsByGameId(Long gameId);
}
