package com.tradelearn.server.game.service;

import com.tradelearn.server.game.model.Game;
import com.tradelearn.server.game.model.GameStatus;
import com.tradelearn.server.game.repository.GameRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Read-only query service for match/game data.
 *
 * <p>All methods in this class are pure reads — no state mutation, no Redis
 * side effects. Inject this wherever you only need to look up game data
 * (e.g. controllers displaying lobby lists, result pages, analytics).
 *
 * <p>Separating read queries from the lifecycle and scoring logic
 * eliminates unnecessary dependency injection in consumers that never
 * perform writes, reducing coupling and making the dependency graph clearer.
 */
@Service
@Transactional(readOnly = true)
public class MatchQueryService {

    private final GameRepository gameRepository;

    public MatchQueryService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    /**
     * Find a single match by ID.
     *
     * @param gameId the match identifier
     * @return the game if found
     */
    public Optional<Game> getMatch(long gameId) {
        return gameRepository.findById(gameId);
    }

    /**
     * All games currently in WAITING state (open lobby games).
     */
    public List<Game> getOpenMatches() {
        return gameRepository.findByStatus(GameStatus.WAITING);
    }

    /**
     * All games currently in ACTIVE state (in-progress games).
     */
    public List<Game> getActiveMatches() {
        return gameRepository.findByStatus(GameStatus.ACTIVE);
    }

    /**
     * All games that have reached the FINISHED state.
     */
    public List<Game> getFinishedMatches() {
        return gameRepository.findByStatus(GameStatus.FINISHED);
    }

    /**
     * All games where the given user is either creator or opponent,
     * regardless of status. Used for match history and profile pages.
     *
     * @param userId the authenticated user's ID
     */
    public List<Game> getUserMatches(long userId) {
        return gameRepository.findByCreatorIdOrOpponentId(userId, userId);
    }
}
