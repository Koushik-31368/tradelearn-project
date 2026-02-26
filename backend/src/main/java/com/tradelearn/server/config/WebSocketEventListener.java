package com.tradelearn.server.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.tradelearn.server.model.Game;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.service.CandleService;
import com.tradelearn.server.service.GameMetricsService;
import com.tradelearn.server.service.MatchSchedulerService;
import com.tradelearn.server.service.PositionSnapshotStore;
import com.tradelearn.server.service.RoomManager;
import com.tradelearn.server.socket.GameBroadcaster;

/**
 * Listens for WebSocket connect/disconnect events.
 * Delegates all session tracking to {@link RoomManager}.
 *
 * On disconnect:
 *   - Unregisters session from RoomManager
 *   - Starts a 15-second reconnection grace period
 *   - If the player does not reconnect, marks the game ABANDONED
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);
    private static final int RECONNECT_GRACE_SECONDS = 15;

    private final GameRepository gameRepository;
    private final MatchSchedulerService matchSchedulerService;
    private final CandleService candleService;
    private final GameBroadcaster broadcaster;
    private final RoomManager roomManager;
    private final TaskScheduler taskScheduler;
    private final PositionSnapshotStore positionStore;
    private final GameMetricsService metrics;

    public WebSocketEventListener(GameRepository gameRepository,
                                  MatchSchedulerService matchSchedulerService,
                                  CandleService candleService,
                                  GameBroadcaster broadcaster,
                                  RoomManager roomManager,
                                  TaskScheduler taskScheduler,
                                  PositionSnapshotStore positionStore,
                                  GameMetricsService metrics) {
        this.gameRepository = gameRepository;
        this.matchSchedulerService = matchSchedulerService;
        this.candleService = candleService;
        this.broadcaster = broadcaster;
        this.roomManager = roomManager;
        this.taskScheduler = taskScheduler;
        this.positionStore = positionStore;
        this.metrics = metrics;
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

        // User info is set during handshake by WebSocketAuthInterceptor
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        Long userId = sessionAttrs != null ? (Long) sessionAttrs.get("userId") : null;
        String username = sessionAttrs != null ? (String) sessionAttrs.get("username") : null;

        log.info("[WS] New connection: sessionId={}, userId={}, username={}", sessionId, userId, username);

        // Try to get gameId from STOMP connect headers
        List<String> gameHeaders = accessor.getNativeHeader("gameId");

        if (userId != null && gameHeaders != null && !gameHeaders.isEmpty()) {
            try {
                long gameId = Long.parseLong(gameHeaders.get(0));
                registerSession(sessionId, userId, gameId);
            } catch (NumberFormatException e) {
                log.warn("[WS] Invalid gameId header on connect");
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

        // ── Reconnection grace period (15 seconds) ──
        if (!roomManager.hasRoom(gameId)) {
            // No room — fall back to immediate abandon
            log.warn("[WS] No room for game {} — immediate abandon", gameId);
            doAbandon(gameId, userId, game);
            return;
        }

        roomManager.markDisconnected(gameId, userId);

        String disconnectedUsername = isCreator
                ? game.getCreator().getUsername()
                : (game.getOpponent() != null ? game.getOpponent().getUsername() : "Unknown");

        log.info("[WS] Player {} ({}) disconnected from game {} — starting {}s grace period",
                userId, disconnectedUsername, gameId, RECONNECT_GRACE_SECONDS);

        // Schedule abandon after grace period
        ScheduledFuture<?> timer = taskScheduler.schedule(
                () -> handleReconnectTimeout(gameId, userId),
                Instant.now().plusSeconds(RECONNECT_GRACE_SECONDS)
        );
        roomManager.setReconnectTimer(gameId, userId, timer);

        // Notify opponent that player is reconnecting
        Long remainingPlayerId = isCreator
                ? (game.getOpponent() != null ? game.getOpponent().getId() : null)
                : game.getCreator().getId();

        broadcaster.sendToGame(gameId, "player-reconnecting",
                Map.of(
                        "gameId", gameId,
                        "disconnectedUserId", userId,
                        "disconnectedUsername", disconnectedUsername,
                        "remainingPlayerId", remainingPlayerId != null ? remainingPlayerId : -1,
                        "deadlineMs", System.currentTimeMillis() + (RECONNECT_GRACE_SECONDS * 1000L),
                        "message", disconnectedUsername + " disconnected. Waiting " + RECONNECT_GRACE_SECONDS + "s for reconnection..."
                )
        );
    }

    // ==================== RECONNECT TIMEOUT ====================

    /**
     * Called when the grace period expires without reconnection.
     */
    private void handleReconnectTimeout(long gameId, long userId) {
        // If the player already reconnected, do nothing
        if (roomManager.hasRoom(gameId) && !roomManager.isDisconnected(gameId, userId)) {
            log.info("[WS] Player {} reconnected to game {} before timeout — no action", userId, gameId);
            return;
        }

        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null || !"ACTIVE".equals(game.getStatus())) {
            log.debug("[WS] Game {} no longer ACTIVE after timeout — skipping abandon", gameId);
            return;
        }

        log.warn("[WS] Player {} failed to reconnect to game {} within {}s — abandoning",
                userId, gameId, RECONNECT_GRACE_SECONDS);

        metrics.recordReconnectTimeout();

        doAbandon(gameId, userId, game);
    }

    /**
     * Execute the full abandon sequence: stop scheduler, mark ABANDONED, cleanup, notify.
     */
    private void doAbandon(long gameId, long userId, Game game) {
        // Stop candle progression
        matchSchedulerService.stopProgression(gameId);

        // Mark game as abandoned in DB
        game.setStatus("ABANDONED");
        gameRepository.save(game);

        // Free candle cache
        candleService.evict(gameId);

        // Evict position snapshots
        positionStore.evictGame(gameId);

        // Clean up room (sets phase ABANDONED, cancels scheduler + reconnect timers, removes room)
        roomManager.cancelAllReconnectTimers(gameId);
        roomManager.endGame(gameId, true);

        metrics.recordMatchForfeited();

        // Determine the remaining player
        boolean isCreator = game.getCreator() != null && game.getCreator().getId().equals(userId);
        Long remainingPlayerId = isCreator
                ? (game.getOpponent() != null ? game.getOpponent().getId() : null)
                : game.getCreator().getId();

        String disconnectedUsername = isCreator
                ? game.getCreator().getUsername()
                : (game.getOpponent() != null ? game.getOpponent().getUsername() : "Unknown");

        // Notify remaining player
        broadcaster.sendToGame(gameId, "player-disconnected",
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
