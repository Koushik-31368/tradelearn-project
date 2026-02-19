package com.tradelearn.server.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.tradelearn.server.model.Game;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.service.CandleService;
import com.tradelearn.server.service.MatchSchedulerService;

/**
 * Listens for WebSocket connect/disconnect events.
 *
 * On disconnect:
 *   - If the disconnected user is in an ACTIVE game, mark it ABANDONED
 *   - Stop the candle scheduler
 *   - Notify the remaining player
 *
 * On connect:
 *   - Track the session → userId mapping for disconnect lookups
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final GameRepository gameRepository;
    private final MatchSchedulerService matchSchedulerService;
    private final CandleService candleService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Maps WebSocket sessionId → userId.
     * Populated when users subscribe to a game topic (via header or first trade).
     */
    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();

    /**
     * Maps WebSocket sessionId → gameId.
     * Populated when users subscribe to a game topic.
     */
    private final Map<String, Long> sessionGameMap = new ConcurrentHashMap<>();

    public WebSocketEventListener(GameRepository gameRepository,
                                  MatchSchedulerService matchSchedulerService,
                                  CandleService candleService,
                                  SimpMessagingTemplate messagingTemplate) {
        this.gameRepository = gameRepository;
        this.matchSchedulerService = matchSchedulerService;
        this.candleService = candleService;
        this.messagingTemplate = messagingTemplate;
    }

    // ==================== PUBLIC: REGISTER SESSION ====================

    /**
     * Called by GameWebSocketController when a trade or position request
     * arrives, so we know which session belongs to which user/game.
     */
    public void registerSession(String sessionId, long userId, long gameId) {
        sessionUserMap.put(sessionId, userId);
        sessionGameMap.put(sessionId, gameId);
        log.info("[WS] Registered session {} → user {} in game {}", sessionId, userId, gameId);
    }

    // ==================== CONNECT ====================

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        log.info("[WS] New connection: sessionId={}", sessionId);

        // Try to extract userId and gameId from STOMP connect headers (optional)
        List<String> userHeaders = accessor.getNativeHeader("userId");
        List<String> gameHeaders = accessor.getNativeHeader("gameId");

        if (userHeaders != null && !userHeaders.isEmpty() &&
            gameHeaders != null && !gameHeaders.isEmpty()) {
            try {
                long userId = Long.parseLong(userHeaders.get(0));
                long gameId = Long.parseLong(gameHeaders.get(0));
                registerSession(sessionId, userId, gameId);
            } catch (NumberFormatException e) {
                log.warn("[WS] Invalid userId/gameId headers on connect");
            }
        }
    }

    // ==================== DISCONNECT ====================

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        Long userId = sessionUserMap.remove(sessionId);
        Long gameId = sessionGameMap.remove(sessionId);

        log.info("[WS] Disconnect: sessionId={}, userId={}, gameId={}",
                sessionId, userId, gameId);

        if (userId == null || gameId == null) {
            log.debug("[WS] Disconnect from untracked session {}", sessionId);
            return;
        }

        // Look up the game
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null) return;

        // Only handle disconnect for ACTIVE games
        if (!"ACTIVE".equals(game.getStatus())) {
            log.debug("[WS] Game {} is {} — ignoring disconnect", gameId, game.getStatus());
            return;
        }

        // Verify the disconnected user is actually a participant
        boolean isCreator = game.getCreator() != null && game.getCreator().getId().equals(userId);
        boolean isOpponent = game.getOpponent() != null && game.getOpponent().getId().equals(userId);

        if (!isCreator && !isOpponent) {
            log.debug("[WS] User {} is not a participant in game {} — ignoring", userId, gameId);
            return;
        }

        log.warn("[WS] Player {} disconnected from ACTIVE game {} — marking ABANDONED",
                userId, gameId);

        // Stop candle progression
        matchSchedulerService.stopProgression(gameId);

        // Mark game as abandoned
        game.setStatus("ABANDONED");
        gameRepository.save(game);

        // Free candle cache
        candleService.evict(gameId);

        // Determine the remaining player
        Long remainingPlayerId = isCreator
                ? (game.getOpponent() != null ? game.getOpponent().getId() : null)
                : game.getCreator().getId();

        String disconnectedUsername = isCreator
                ? game.getCreator().getUsername()
                : (game.getOpponent() != null ? game.getOpponent().getUsername() : "Unknown");

        // Notify remaining player
        messagingTemplate.convertAndSend(
                "/topic/game/" + gameId + "/player-disconnected",
                Map.of(
                        "gameId", gameId,
                        "disconnectedUserId", userId,
                        "disconnectedUsername", disconnectedUsername,
                        "remainingPlayerId", remainingPlayerId != null ? remainingPlayerId : -1,
                        "status", "ABANDONED",
                        "message", disconnectedUsername + " disconnected. Game abandoned."
                )
        );

        log.info("[WS] Game {} marked ABANDONED. Notified remaining player {}",
                gameId, remainingPlayerId);
    }

    // ==================== DIAGNOSTICS ====================

    public int getActiveSessionCount() {
        return sessionUserMap.size();
    }

    public Map<String, Long> getSessionUserMap() {
        return Map.copyOf(sessionUserMap);
    }
}
