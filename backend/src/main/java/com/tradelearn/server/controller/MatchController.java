
    @PostMapping("/{matchId}/rematch")
    public ResponseEntity<?> rematch(@PathVariable String matchId) {
        User user = getAuthenticatedUser();
        Match newMatch = matchService.requestRematch(matchId, user.getId().toString());
        if (newMatch == null) {
            return ResponseEntity.ok("Waiting for opponent...");
        }
        // Notify both players via WebSocket (if GameBroadcaster available)
        Match oldMatch = matchService.getMatch(matchId);
        try {
            var gameBroadcasterField = matchService.getClass().getDeclaredField("broadcaster");
            gameBroadcasterField.setAccessible(true);
            Object broadcaster = gameBroadcasterField.get(matchService);
            if (broadcaster != null) {
                broadcaster.getClass().getMethod("sendToUser", String.class, String.class, Object.class)
                    .invoke(broadcaster, oldMatch.getPlayer1(), "/queue/rematch", newMatch.getMatchId());
                broadcaster.getClass().getMethod("sendToUser", String.class, String.class, Object.class)
                    .invoke(broadcaster, oldMatch.getPlayer2(), "/queue/rematch", newMatch.getMatchId());
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(newMatch.getMatchId());
    }
package com.tradelearn.server.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.dto.CreateMatchRequest;
import com.tradelearn.server.dto.EndMatchRequest;
import com.tradelearn.server.dto.MatchResult;
import com.tradelearn.server.dto.MatchTradeRequest;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.MatchStats;
import com.tradelearn.server.model.Trade;
import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.MatchStatsRepository;
import com.tradelearn.server.service.CandleService;
import com.tradelearn.server.service.MatchService;
import com.tradelearn.server.service.MatchTradeService;
import com.tradelearn.server.service.RoomManager;

import jakarta.validation.Valid;

/**
 * REST controller for the 1v1 match lifecycle.
 *
 * All mutating endpoints extract the authenticated user from the JWT token
 * via SecurityContextHolder — userId is NEVER accepted from the client.
 *
 * Endpoints:
 *   POST /api/match/create        — Create a new match (WAITING)
 *   POST /api/match/{id}/join     — Join an open match (WAITING → ACTIVE)
 *   POST /api/match/{id}/start    — Explicitly start (if not auto-started)
 *   POST /api/match/end           — End match, calculate results
 *   POST /api/match/trade         — Place a trade in an active match
 *   GET  /api/match/open          — List WAITING matches
 *   GET  /api/match/active        — List ACTIVE matches
 *   GET  /api/match/finished      — List FINISHED matches
 *   GET  /api/match/{id}          — Get a specific match
 *   GET  /api/match/{id}/trades   — Get all trades in a match
 *   GET  /api/match/{id}/stats    — Get match stats
 *   GET  /api/match/rooms         — Room diagnostics
 */
@RestController
@RequestMapping("/api/match")
public class MatchController {

    private final MatchService matchService;
    private final MatchTradeService matchTradeService;
    private final CandleService candleService;
    private final MatchStatsRepository matchStatsRepository;
    private final RoomManager roomManager;

    public MatchController(MatchService matchService,
                           MatchTradeService matchTradeService,
                           CandleService candleService,
                           MatchStatsRepository matchStatsRepository,
                           RoomManager roomManager) {
        this.matchService = matchService;
        this.matchTradeService = matchTradeService;
        this.candleService = candleService;
        this.matchStatsRepository = matchStatsRepository;
        this.roomManager = roomManager;
    }

    // ==================== HELPER ====================

    /**
     * Extract the authenticated User from the SecurityContext.
     * The JwtAuthenticationFilter sets the full User entity as the principal.
     */
    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new IllegalStateException("Not authenticated");
        }
        return (User) auth.getPrincipal();
    }

    // ==================== MATCH LIFECYCLE ====================

    /**
     * POST /api/match/create
     * Create a new 1v1 match. Creator ID extracted from JWT.
     */
    @PostMapping("/create")
    public ResponseEntity<?> createMatch(@Valid @RequestBody CreateMatchRequest request) {
        try {
            User creator = getAuthenticatedUser();
            request.setCreatorId(creator.getId());
            Game game = matchService.createMatch(request);
            return ResponseEntity.ok(game);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/match/{gameId}/join
     * Join an open match. User ID extracted from JWT — no query param needed.
     */
    @PostMapping("/{gameId}/join")
    public ResponseEntity<?> joinMatch(@PathVariable long gameId) {
        try {
            User joiner = getAuthenticatedUser();
            Game game = matchService.joinMatch(gameId, joiner.getId());
            return ResponseEntity.ok(game);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/match/{gameId}/start
     * Explicitly start the match timer. Only participants can start.
     */
    @PostMapping("/{gameId}/start")
    public ResponseEntity<?> startMatch(@PathVariable long gameId) {
        try {
            User user = getAuthenticatedUser();
            Game game = matchService.getMatch(gameId).orElse(null);
            if (game == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Game not found"));
            }
            boolean isParticipant =
                (game.getCreator() != null && game.getCreator().getId().equals(user.getId())) ||
                (game.getOpponent() != null && game.getOpponent().getId().equals(user.getId()));
            if (!isParticipant) {
                return ResponseEntity.status(403).body(Map.of("error", "You are not a participant in this match"));
            }

            Game started = matchService.startMatch(gameId);
            return ResponseEntity.ok(started);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/match/end
     * End match, calculate profits, determine winner.
     * Only participants of the match can end it.
     */
    @PostMapping("/end")
    public ResponseEntity<?> endMatch(@RequestBody EndMatchRequest request) {
        try {
            User user = getAuthenticatedUser();
            // Verify the user is a participant
            Game game = matchService.getMatch(request.getGameId()).orElse(null);
            if (game == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Game not found"));
            }
            boolean isParticipant = 
                (game.getCreator() != null && game.getCreator().getId().equals(user.getId())) ||
                (game.getOpponent() != null && game.getOpponent().getId().equals(user.getId()));
            if (!isParticipant) {
                return ResponseEntity.status(403).body(Map.of("error", "You are not a participant in this match"));
            }

            MatchResult result = matchService.endMatch(request.getGameId());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== TRADING ====================

    /**
     * POST /api/match/trade
     * Place a trade. User ID extracted from JWT — never from request body.
     */
    @PostMapping("/trade")
    public ResponseEntity<?> placeTrade(@Valid @RequestBody MatchTradeRequest request) {
        try {
            User user = getAuthenticatedUser();
            request.setUserId(user.getId());
            Trade trade = matchTradeService.placeTrade(request);
            return ResponseEntity.ok(trade);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== QUERIES ====================

    @GetMapping("/open")
    public ResponseEntity<List<Game>> getOpenMatches() {
        return ResponseEntity.ok(matchService.getOpenMatches());
    }

    @GetMapping("/active")
    public ResponseEntity<List<Game>> getActiveMatches() {
        return ResponseEntity.ok(matchService.getActiveMatches());
    }

    @GetMapping("/finished")
    public ResponseEntity<List<Game>> getFinishedMatches() {
        return ResponseEntity.ok(matchService.getFinishedMatches());
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<?> getMatch(@PathVariable long gameId) {
        return matchService.getMatch(gameId)
                .map(game -> ResponseEntity.ok((Object) game))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/match/user/me
     * Get all matches for the authenticated user.
     */
    @GetMapping("/user/me")
    public ResponseEntity<List<Game>> getMyMatches() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(matchService.getUserMatches(user.getId()));
    }

    /**
     * GET /api/match/user/{userId}
     * Get all matches for a user (public, for profiles).
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Game>> getUserMatches(@PathVariable long userId) {
        return ResponseEntity.ok(matchService.getUserMatches(userId));
    }

    @GetMapping("/{gameId}/trades")
    public ResponseEntity<List<Trade>> getGameTrades(@PathVariable long gameId) {
        return ResponseEntity.ok(matchTradeService.getGameTrades(gameId));
    }

    /**
     * GET /api/match/{gameId}/trades/me
     * Get trades for the authenticated player in a match.
     */
    @GetMapping("/{gameId}/trades/me")
    public ResponseEntity<List<Trade>> getMyTrades(@PathVariable long gameId) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(matchTradeService.getPlayerGameTrades(gameId, user.getId()));
    }

    @GetMapping("/{gameId}/trades/{userId}")
    public ResponseEntity<List<Trade>> getPlayerTrades(
            @PathVariable long gameId,
            @PathVariable long userId
    ) {
        return ResponseEntity.ok(matchTradeService.getPlayerGameTrades(gameId, userId));
    }

    /**
     * GET /api/match/{gameId}/position/me
     * Get current position for the authenticated player.
     */
    @GetMapping("/{gameId}/position/me")
    public ResponseEntity<?> getMyPosition(@PathVariable long gameId) {
        User user = getAuthenticatedUser();
        Game game = matchService.getMatch(gameId).orElse(null);
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        MatchTradeService.PlayerPosition position = matchTradeService.getPlayerPosition(
                gameId, user.getId(), game.getStartingBalance()
        );
        return ResponseEntity.ok(position);
    }

    @GetMapping("/{gameId}/position/{userId}")
    public ResponseEntity<?> getPlayerPosition(
            @PathVariable long gameId,
            @PathVariable long userId
    ) {
        Game game = matchService.getMatch(gameId).orElse(null);
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        MatchTradeService.PlayerPosition position = matchTradeService.getPlayerPosition(
                gameId, userId, game.getStartingBalance()
        );
        return ResponseEntity.ok(position);
    }

    // ==================== CANDLE DATA ====================

    @GetMapping("/{gameId}/candle")
    public ResponseEntity<?> getCurrentCandle(@PathVariable long gameId) {
        try {
            CandleService.Candle candle = candleService.getCurrentCandle(gameId);
            return ResponseEntity.ok(candle);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{gameId}/candle/advance")
    public ResponseEntity<?> advanceCandle(@PathVariable long gameId) {
        try {
            CandleService.Candle next = candleService.advanceCandle(gameId);
            if (next == null) {
                return ResponseEntity.ok(Map.of(
                        "status", "LAST_CANDLE",
                        "message", "No more candles — game should be ended"
                ));
            }
            return ResponseEntity.ok(next);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/candle/price")
    public ResponseEntity<?> getCurrentPrice(@PathVariable long gameId) {
        try {
            double price = candleService.getCurrentPrice(gameId);
            return ResponseEntity.ok(Map.of("price", price));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{gameId}/candle/remaining")
    public ResponseEntity<?> getRemainingCandles(@PathVariable long gameId) {
        try {
            return ResponseEntity.ok(Map.of(
                    "remaining", candleService.getRemainingCandles(gameId),
                    "hasMore", candleService.hasMoreCandles(gameId)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== STATS ====================

    @GetMapping("/{gameId}/stats")
    public ResponseEntity<?> getMatchStats(@PathVariable long gameId) {
        List<MatchStats> stats = matchStatsRepository.findByGameId(gameId);
        if (stats.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No stats available yet"));
        }
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{gameId}/stats/{userId}")
    public ResponseEntity<?> getPlayerStats(@PathVariable long gameId,
                                            @PathVariable long userId) {
        return matchStatsRepository.findByGameIdAndUserId(gameId, userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(Map.of("message", "No stats available for this player")));
    }

    // ==================== ROOM DIAGNOSTICS ====================

    @GetMapping("/rooms")
    public ResponseEntity<?> getRoomSnapshots() {
        return ResponseEntity.ok(Map.of(
            "totalRooms",  roomManager.totalRoomCount(),
            "activeRooms", roomManager.activeRoomCount(),
            "activeSessions", roomManager.activeSessionCount(),
            "rooms",       roomManager.allRoomSnapshots()
        ));
    }

    @GetMapping("/rooms/{gameId}")
    public ResponseEntity<?> getRoomSnapshot(@PathVariable long gameId) {
        Map<String, Object> snapshot = roomManager.getRoomSnapshot(gameId);
        if (snapshot == null) {
            return ResponseEntity.ok(Map.of("message", "No active room for game " + gameId));
        }
        return ResponseEntity.ok(snapshot);
    }
}
