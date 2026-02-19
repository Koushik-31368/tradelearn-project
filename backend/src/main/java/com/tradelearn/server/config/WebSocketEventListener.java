package com.tradelearn.server.config;

import java.util.List;
import java.util.Map;

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
import com.tradelearn.server.service.RoomManager;

/**
 * Listens for WebSocket connect/disconnect events.
 * Delegates all session tracking to {@link RoomManager}.
 *
 * On disconnect:
 *   - Unregisters session from RoomManager
 *   - If the disconnected user was in an ACTIVE game, marks it ABANDONED
 *   - Stops candle scheduler, notifies remaining player
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final GameRepository gameRepository;
    private final MatchSchedulerService matchSchedulerService;
    private final CandleService candleService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RoomManager roomManager;

    public WebSocketEventListener(GameRepository gameRepository,
                                  MatchSchedulerService matchSchedulerService,
                                  CandleService candleService,
                                  SimpMessagingTemplate messagingTemplate,
                                  RoomManager roomManager) {
        this.gameRepository = gameRepository;
        this.matchSchedulerService = matchSchedulerService;
        this.candleService = candleService;
        this.messagingTemplate = messagingTemplate;
        this.roomManager = roomManager;
    }

    // ==================== PUBLIC: REGISTER SESSION ====================

    /**
     * Called by GameWebSocketController when a trade or position request
     * arrives, so we know which session belongs to which user/game.
     * Delegates to RoomManager.
     */
    public void registerSession(String sessionId, long userId, long gameId) {
        roomManager.registerSession(sessionId, userId, gameId);
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

        // Delegate to RoomManager — returns disconnect info or null
        RoomManager.DisconnectInfo info = roomManager.unregisterSession(sessionId);

        if (info == null) {
            log.debug("[WS] Disconnect from untracked session {}", sessionId);
            return;
        }

        long userId = info.userId();
        long gameId = info.gameId();

        log.info("[WS] Disconnect: sessionId={}, userId={}, gameId={}, remainingPlayers={}",
                sessionId, userId, gameId, info.remainingPlayers());

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

        // Mark game as abandoned in DB
        game.setStatus("ABANDONED");
        gameRepository.save(game);

        // Free candle cache
        candleService.evict(gameId);

        // Clean up room (sets phase ABANDONED, cancels scheduler, removes room)
        roomManager.endGame(gameId, true);

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
        return roomManager.activeSessionCount();
    }
}
