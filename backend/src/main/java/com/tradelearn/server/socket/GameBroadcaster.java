package com.tradelearn.server.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Abstraction over WebSocket message delivery.
 *
 * Single instance → delegates directly to SimpMessagingTemplate.
 * Multi-instance  → routes through Redis Pub/Sub relay so all
 *                   connected clients receive the message
 *                   regardless of which server instance they're on.
 *
 * All services should inject GameBroadcaster instead of
 * SimpMessagingTemplate directly. This makes horizontal scaling
 * transparent to business logic.
 *
 * Usage:
 *   broadcaster.sendToGame(gameId, "candle", candlePayload);
 *   broadcaster.sendToGame(gameId, "started", startPayload);
 *   broadcaster.sendToGame(gameId, "finished", resultPayload);
 */
@Component
public class GameBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(GameBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisWebSocketRelay redisRelay;

    public GameBroadcaster(SimpMessagingTemplate messagingTemplate,
                           RedisWebSocketRelay redisRelay) {
        this.messagingTemplate = messagingTemplate;
        this.redisRelay = redisRelay;
    }

    /**
     * Broadcast a game event to all connected clients.
     *
     * @param gameId    The game ID
     * @param eventType The event type (e.g., "candle", "started", "finished", "trade")
     * @param payload   The data to send
     */
    public void sendToGame(long gameId, String eventType, Object payload) {
        String destination = "/topic/game/" + gameId + "/" + eventType;

        // Local delivery (clients on this instance)
        messagingTemplate.convertAndSend(destination, payload);

        // Cross-instance delivery via Redis
        try {
            redisRelay.broadcast(destination, payload);
        } catch (Exception e) {
            log.warn("[Broadcaster] Redis relay failed for game {} event {}: {}",
                    gameId, eventType, e.getMessage());
            // Local delivery already happened — game still works on this instance
        }
    }

    /**
     * Send an error to a specific user's error queue.
     *
     * @param userId  The target user ID
     * @param gameId  The game context
     * @param message The error message
     */
    public void sendErrorToUser(long userId, long gameId, String message) {
        String destination = "/topic/game/" + gameId + "/error/" + userId;
        messagingTemplate.convertAndSend(destination,
                java.util.Map.of("error", message, "gameId", gameId));
    }

    /**
     * Send a user-targeted event (e.g., match-found notification).
     * Routes through Redis relay for horizontal scaling.
     *
     * @param userId    The target user ID
     * @param eventType The event type (e.g., "match-found")
     * @param payload   The data to send
     */
    public void sendToUser(long userId, String eventType, Object payload) {
        String destination = "/topic/user/" + userId + "/" + eventType;

        // Local delivery
        messagingTemplate.convertAndSend(destination, payload);

        // Cross-instance delivery via Redis
        try {
            redisRelay.broadcast(destination, payload);
        } catch (Exception e) {
            log.warn("[Broadcaster] Redis relay failed for user {} event {}: {}",
                    userId, eventType, e.getMessage());
        }
    }

    /**
     * Direct send without Redis relay (for instance-local events).
     */
    public void sendLocal(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }
}
