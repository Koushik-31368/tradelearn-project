package com.tradelearn.server.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.dto.CreateMatchRequest;
import com.tradelearn.server.dto.EndMatchRequest;
import com.tradelearn.server.dto.MatchResult;
import com.tradelearn.server.dto.MatchTradeRequest;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.MatchStats;
import com.tradelearn.server.model.Trade;
import com.tradelearn.server.repository.MatchStatsRepository;
import com.tradelearn.server.service.CandleService;
import com.tradelearn.server.service.MatchService;
import com.tradelearn.server.service.MatchTradeService;
import com.tradelearn.server.service.RoomManager;

import jakarta.validation.Valid;

/**
 * REST controller for the 1v1 match lifecycle.
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
 *
 * All request bodies validated with Jakarta Validation (@Valid).
 * Exceptions caught by GlobalExceptionHandler → structured JSON errors.
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

    // ==================== MATCH LIFECYCLE ====================

    /**
     * POST /api/match/create
     * Create a new 1v1 match (status: WAITING)
     */
    @PostMapping("/create")
    public ResponseEntity<?> createMatch(@Valid @RequestBody CreateMatchRequest request) {
        try {
            Game game = matchService.createMatch(request);
            return ResponseEntity.ok(game);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/match/{gameId}/join?userId=123
     * Join an open match (status: WAITING → ACTIVE)
     */
    @PostMapping("/{gameId}/join")
    public ResponseEntity<?> joinMatch(
            @PathVariable long gameId,
            @RequestParam long userId
    ) {
        try {
            Game game = matchService.joinMatch(gameId, userId);
            return ResponseEntity.ok(game);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/match/{gameId}/start
     * Explicitly start the match timer
     */
    @PostMapping("/{gameId}/start")
    public ResponseEntity<?> startMatch(@PathVariable long gameId) {
        try {
            Game game = matchService.startMatch(gameId);
            return ResponseEntity.ok(game);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/match/end
     * End match, calculate profits, determine winner
     */
    @PostMapping("/end")
    public ResponseEntity<?> endMatch(@RequestBody EndMatchRequest request) {
        try {
            MatchResult result = matchService.endMatch(request.getGameId());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== TRADING ====================

    /**
     * POST /api/match/trade
     * Place a trade within an active match
     */
    @PostMapping("/trade")
    public ResponseEntity<?> placeTrade(@Valid @RequestBody MatchTradeRequest request) {
        try {
            Trade trade = matchTradeService.placeTrade(request);
            return ResponseEntity.ok(trade);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== QUERIES ====================

    /**
     * GET /api/match/open
     * List all matches waiting for an opponent
     */
    @GetMapping("/open")
    public ResponseEntity<List<Game>> getOpenMatches() {
        return ResponseEntity.ok(matchService.getOpenMatches());
    }

    /**
     * GET /api/match/active
     * List all currently active matches
     */
    @GetMapping("/active")
    public ResponseEntity<List<Game>> getActiveMatches() {
        return ResponseEntity.ok(matchService.getActiveMatches());
    }

    /**
     * GET /api/match/finished
     * List all completed matches
     */
    @GetMapping("/finished")
    public ResponseEntity<List<Game>> getFinishedMatches() {
        return ResponseEntity.ok(matchService.getFinishedMatches());
    }

    /**
     * GET /api/match/{gameId}
     * Get a specific match
     */
    @GetMapping("/{gameId}")
    public ResponseEntity<?> getMatch(@PathVariable long gameId) {
        return matchService.getMatch(gameId)
                .map(game -> ResponseEntity.ok((Object) game))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/match/user/{userId}
     * Get all matches for a user (as creator or opponent)
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Game>> getUserMatches(@PathVariable long userId) {
        return ResponseEntity.ok(matchService.getUserMatches(userId));
    }

    /**
     * GET /api/match/{gameId}/trades
     * Get all trades in a match
     */
    @GetMapping("/{gameId}/trades")
    public ResponseEntity<List<Trade>> getGameTrades(@PathVariable long gameId) {
        return ResponseEntity.ok(matchTradeService.getGameTrades(gameId));
    }

    /**
     * GET /api/match/{gameId}/trades/{userId}
     * Get trades for a specific player in a match
     */
    @GetMapping("/{gameId}/trades/{userId}")
    public ResponseEntity<List<Trade>> getPlayerTrades(
            @PathVariable long gameId,
            @PathVariable long userId
    ) {
        return ResponseEntity.ok(matchTradeService.getPlayerGameTrades(gameId, userId));
    }

    /**
     * GET /api/match/{gameId}/position/{userId}
     * Get current position for a player in a match
     */
    @GetMapping("/{gameId}/position/{userId}")
    public ResponseEntity<?> getPlayerPosition(
            @PathVariable long gameId,
            @PathVariable long userId
    ) {
        Game game = matchService.getMatch(gameId)
                .orElse(null);
        if (game == null) {
            return ResponseEntity.notFound().build();
        }

        MatchTradeService.PlayerPosition position = matchTradeService.getPlayerPosition(
                gameId, userId, game.getStartingBalance()
        );
        return ResponseEntity.ok(position);
    }

    // ==================== CANDLE DATA ====================

    /**
     * GET /api/match/{gameId}/candle
     * Get the current candle (server-authoritative OHLCV)
     */
    @GetMapping("/{gameId}/candle")
    public ResponseEntity<?> getCurrentCandle(@PathVariable long gameId) {
        try {
            CandleService.Candle candle = candleService.getCurrentCandle(gameId);
            return ResponseEntity.ok(candle);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/match/{gameId}/candle/advance
     * Advance to the next candle (next trading round)
     */
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

    /**
     * GET /api/match/{gameId}/candle/price
     * Get current server-authoritative price
     */
    @GetMapping("/{gameId}/candle/price")
    public ResponseEntity<?> getCurrentPrice(@PathVariable long gameId) {
        try {
            double price = candleService.getCurrentPrice(gameId);
            return ResponseEntity.ok(Map.of("price", price));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/match/{gameId}/candle/remaining
     * Get how many candles are left
     */
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

    /**
     * GET /api/match/{gameId}/stats
     * Get risk/performance stats for all players in a finished game.
     */
    @GetMapping("/{gameId}/stats")
    public ResponseEntity<?> getMatchStats(@PathVariable long gameId) {
        List<MatchStats> stats = matchStatsRepository.findByGameId(gameId);
        if (stats.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No stats available yet"));
        }
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/match/{gameId}/stats/{userId}
     * Get risk/performance stats for a specific player in a finished game.
     */
    @GetMapping("/{gameId}/stats/{userId}")
    public ResponseEntity<?> getPlayerStats(@PathVariable long gameId,
                                            @PathVariable long userId) {
        return matchStatsRepository.findByGameIdAndUserId(gameId, userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(Map.of("message", "No stats available for this player")));
    }

    // ==================== ROOM DIAGNOSTICS ====================

    /**
     * GET /api/match/rooms
     * Returns snapshots of all in-memory rooms from RoomManager.
     * Useful for debugging active games in production.
     */
    @GetMapping("/rooms")
    public ResponseEntity<?> getRoomSnapshots() {
        return ResponseEntity.ok(Map.of(
            "totalRooms",  roomManager.totalRoomCount(),
            "activeRooms", roomManager.activeRoomCount(),
            "activeSessions", roomManager.activeSessionCount(),
            "rooms",       roomManager.allRoomSnapshots()
        ));
    }

    /**
     * GET /api/match/rooms/{gameId}
     * Returns snapshot of a single room.
     */
    @GetMapping("/rooms/{gameId}")
    public ResponseEntity<?> getRoomSnapshot(@PathVariable long gameId) {
        RoomManager.Room room = roomManager.getRoom(gameId);
        if (room == null) {
            return ResponseEntity.ok(Map.of("message", "No active room for game " + gameId));
        }
        return ResponseEntity.ok(room.snapshot());
    }
}
