package com.tradelearn.server.game.service;

import com.tradelearn.server.dto.CreateMatchRequest;
import com.tradelearn.server.dto.MatchResult;
import com.tradelearn.server.game.model.Game;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Backward-compatible façade for the match service layer.
 *
 * <p>This class exists solely to preserve the existing call-sites in
 * {@link com.tradelearn.server.game.controller.MatchController},
 * {@link com.tradelearn.server.infrastructure.scheduling.MatchSchedulerService},
 * {@link com.tradelearn.server.websocket.GameWebSocketHandler}, and
 * {@link com.tradelearn.server.matchmaking.service.MatchmakingService}
 * without requiring a mass-rename refactor.
 *
 * <h3>Implementation delegated to:</h3>
 * <ul>
 *   <li>{@link MatchLifecycleService} — create, join, start, auto-match, delete</li>
 *   <li>{@link MatchScoringService} — end, abandon, rematch, ELO</li>
 *   <li>{@link MatchQueryService} — read-only queries</li>
 * </ul>
 *
 * <p><strong>Do not add new business logic here.</strong> Add it in the
 * appropriate focused service and expose it via a new method below if required.
 *
 * @deprecated Inject the focused services directly in new code.
 *             This façade will be removed once all call-sites have been migrated.
 */
@Service
public class MatchService {

    private final MatchLifecycleService lifecycle;
    private final MatchScoringService scoring;
    private final MatchQueryService query;

    public MatchService(MatchLifecycleService lifecycle,
                        MatchScoringService scoring,
                        MatchQueryService query) {
        this.lifecycle = lifecycle;
        this.scoring = scoring;
        this.query = query;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public Game createMatch(CreateMatchRequest request) {
        return lifecycle.createMatch(request);
    }

    public Game joinMatch(long gameId, Long userId) {
        return lifecycle.joinMatch(gameId, userId);
    }

    public Game createAutoMatch(long userId1, long userId2) {
        return lifecycle.createAutoMatch(userId1, userId2);
    }

    public Game startMatch(long gameId) {
        return lifecycle.startMatch(gameId);
    }

    public void deleteGame(long gameId, long requesterId) {
        lifecycle.deleteGame(gameId, requesterId);
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    public MatchResult endMatch(long gameId) {
        return scoring.endMatch(gameId);
    }

    public void forceFinishOnAbandon(long gameId, long disconnectedUserId) {
        scoring.forceFinishOnAbandon(gameId, disconnectedUserId);
    }

    public Map<String, Object> requestRematch(long oldGameId, long userId) {
        return scoring.requestRematch(oldGameId, userId);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public Optional<Game> getMatch(long gameId) {
        return query.getMatch(gameId);
    }

    public List<Game> getOpenMatches() {
        return query.getOpenMatches();
    }

    public List<Game> getActiveMatches() {
        return query.getActiveMatches();
    }

    public List<Game> getFinishedMatches() {
        return query.getFinishedMatches();
    }

    public List<Game> getUserMatches(long userId) {
        return query.getUserMatches(userId);
    }
}
