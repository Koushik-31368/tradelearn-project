package com.tradelearn.server.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.dto.PlayerTicket;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.User;
import com.tradelearn.server.service.MatchmakingService;

/**
 * REST controller for the ranked matchmaking queue.
 *
 * Endpoints:
 *   POST   /api/matchmaking/queue   — Join the ranked queue (may return instant match)
 *   DELETE /api/matchmaking/queue   — Leave the ranked queue (cancel search)
 *   GET    /api/matchmaking/status  — Queue status for the current user
 *
 * All endpoints require JWT authentication. The userId is extracted
 * from the JWT token — never accepted from the client.
 *
 * POST /queue returns one of:
 *   - {@code {"status":"MATCHED","gameId":...}} — instant match found
 *   - {@code {"status":"QUEUED",...}} — player queued, wait for WS notification
 */
@RestController
@RequestMapping("/api/matchmaking")
public class MatchmakingController {

    private final MatchmakingService matchmakingService;

    public MatchmakingController(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new IllegalStateException("Not authenticated");
        }
        return (User) auth.getPrincipal();
    }

    /**
     * Join the ranked matchmaking queue.
     * Creates a PlayerTicket from the authenticated user's rating.
     *
     * If an opponent with a compatible rating is already waiting,
     * the match is created instantly and the response contains the game details.
     * Otherwise the player is queued and will receive a WebSocket
     * {@code match-found} event when paired.
     */
    @PostMapping("/queue")
    public ResponseEntity<?> joinQueue() {
        try {
            User user = getAuthenticatedUser();
            PlayerTicket ticket = new PlayerTicket(
                    user.getId(), user.getUsername(), user.getRating());

            Optional<Game> instantMatch = matchmakingService.enqueue(ticket);

            if (instantMatch.isPresent()) {
                Game game = instantMatch.get();
                return ResponseEntity.ok(Map.of(
                        "status", "MATCHED",
                        "gameId", game.getId(),
                        "stockSymbol", game.getStockSymbol(),
                        "durationMinutes", game.getDurationMinutes(),
                        "startingBalance", game.getStartingBalance()
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "status", "QUEUED",
                    "message", "Searching for opponent...",
                    "rating", user.getRating(),
                    "queueSize", matchmakingService.queueSize()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Leave the ranked matchmaking queue (cancel search).
     */
    @DeleteMapping("/queue")
    public ResponseEntity<?> leaveQueue() {
        User user = getAuthenticatedUser();
        boolean removed = matchmakingService.dequeue(user.getId());
        if (removed) {
            return ResponseEntity.ok(Map.of(
                    "status", "CANCELLED",
                    "message", "Left matchmaking queue"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "status", "NOT_IN_QUEUE",
                "message", "You were not in the queue"
        ));
    }

    /**
     * Get the current matchmaking status for the authenticated user.
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        User user = getAuthenticatedUser();
        boolean queued = matchmakingService.isQueued(user.getId());
        long waitTime = matchmakingService.getWaitTime(user.getId());

        return ResponseEntity.ok(Map.of(
                "queued", queued,
                "waitSeconds", waitTime,
                "queueSize", matchmakingService.queueSize()
        ));
    }
}
