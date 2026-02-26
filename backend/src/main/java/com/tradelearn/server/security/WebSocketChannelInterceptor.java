package com.tradelearn.server.security;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.tradelearn.server.service.RoomManager;

/**
 * STOMP channel interceptor that:
 * <ul>
 *   <li><b>CONNECT</b> — attaches the authenticated user (from handshake) as the STOMP principal</li>
 *   <li><b>SUBSCRIBE</b> — validates that the user is a participant in game-scoped topics,
 *       preventing topic snooping (subscribing to other players' game channels)</li>
 * </ul>
 *
 * Subscription authorization rules:
 * <pre>
 *   /topic/game/{gameId}/**       → user must be a connected player in that game room
 *   /topic/user/{userId}/**       → userId in topic must match the authenticated user
 *   /topic/greetings, /topic/lobby/** → open (no game context)
 *   everything else               → denied
 * </pre>
 */
@Component
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketChannelInterceptor.class);

    /** Matches /topic/game/{gameId}/... — captures gameId */
    private static final Pattern GAME_TOPIC_PATTERN =
            Pattern.compile("^/topic/game/(\\d+)/.*$");

    /** Matches /topic/user/{userId}/... — captures userId */
    private static final Pattern USER_TOPIC_PATTERN =
            Pattern.compile("^/topic/user/(\\d+)/.*$");

    /** Open topics that don't require game membership */
    private static final Set<String> OPEN_TOPIC_PREFIXES = Set.of(
            "/topic/greetings",
            "/topic/lobby",
            "/topic/matchmaking"
    );

    private final RoomManager roomManager;

    public WebSocketChannelInterceptor(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return handleConnect(accessor, message);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return handleSubscribe(accessor, message);
        }

        return message;
    }

    // ==================== CONNECT ====================

    private Message<?> handleConnect(StompHeaderAccessor accessor, Message<?> message) {
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs != null) {
            Long userId = (Long) sessionAttrs.get("userId");
            String username = (String) sessionAttrs.get("username");

            if (userId != null && username != null) {
                accessor.setUser(new StompPrincipal(userId, username));
                log.debug("[WS Channel] Set principal for session {}: userId={}, username={}",
                        accessor.getSessionId(), userId, username);
            }
        }
        return message;
    }

    // ==================== SUBSCRIBE ====================

    private Message<?> handleSubscribe(StompHeaderAccessor accessor, Message<?> message) {
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            log.warn("[WS Channel] SUBSCRIBE with no destination — blocked (session={})",
                    accessor.getSessionId());
            return null; // Block empty subscriptions
        }

        Long userId = getAuthenticatedUserId(accessor);
        if (userId == null) {
            log.warn("[WS Channel] Unauthenticated SUBSCRIBE to {} — blocked", destination);
            return null; // Block unauthenticated subscriptions
        }

        // ── Check if it's an open topic ──
        for (String prefix : OPEN_TOPIC_PREFIXES) {
            if (destination.startsWith(prefix)) {
                return message; // Allowed
            }
        }

        // ── Game-scoped topic: /topic/game/{gameId}/... ──
        Matcher gameMatcher = GAME_TOPIC_PATTERN.matcher(destination);
        if (gameMatcher.matches()) {
            long gameId;
            try {
                gameId = Long.parseLong(gameMatcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("[WS Channel] Invalid gameId in subscription: {} — blocked", destination);
                return null;
            }

            // Verify the user is a participant in this game
            if (!isPlayerInGame(gameId, userId)) {
                log.warn("[WS Channel] User {} not a participant in game {} — SUBSCRIBE to {} blocked",
                        userId, gameId, destination);
                return null; // Topic snooping prevented
            }

            log.debug("[WS Channel] User {} authorized for game topic: {}", userId, destination);
            return message;
        }

        // ── User-scoped topic: /topic/user/{userId}/... ──
        Matcher userMatcher = USER_TOPIC_PATTERN.matcher(destination);
        if (userMatcher.matches()) {
            long targetUserId;
            try {
                targetUserId = Long.parseLong(userMatcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }

            if (targetUserId != userId) {
                log.warn("[WS Channel] User {} tried to subscribe to user {}'s topic: {} — blocked",
                        userId, targetUserId, destination);
                return null;
            }

            return message;
        }

        // ── Unknown topic pattern → deny by default ──
        log.warn("[WS Channel] User {} SUBSCRIBE to unknown topic: {} — blocked", userId, destination);
        return null;
    }

    // ==================== HELPERS ====================

    /**
     * Check if a player is a participant in a game by checking the room's connected players.
     * Falls back gracefully: if room doesn't exist (e.g., not yet created), allows the subscription
     * since the game repository check in the handler will catch unauthorized access.
     */
    private boolean isPlayerInGame(long gameId, long userId) {
        try {
            if (!roomManager.hasRoom(gameId)) {
                // Room may not be created yet (e.g., during match setup) — allow cautiously.
                // The actual trade/position handlers do their own participant verification.
                return true;
            }
            Set<Long> players = roomManager.getConnectedPlayers(gameId);
            return players != null && players.contains(userId);
        } catch (Exception e) {
            // Redis may be down — allow subscription, trade handler has its own auth
            log.debug("[WS Channel] Room lookup failed for game {} — allowing subscription: {}",
                    gameId, e.getMessage());
            return true;
        }
    }

    private Long getAuthenticatedUserId(StompHeaderAccessor accessor) {
        // Try principal first
        Principal principal = accessor.getUser();
        if (principal instanceof StompPrincipal sp) {
            return sp.userId();
        }

        // Fallback: session attributes
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs != null) {
            return (Long) sessionAttrs.get("userId");
        }

        return null;
    }

    /**
     * Simple Principal implementation that carries userId and username.
     */
    public record StompPrincipal(Long userId, String username) implements Principal {
        @Override
        public String getName() {
            return String.valueOf(userId);
        }
    }
}
