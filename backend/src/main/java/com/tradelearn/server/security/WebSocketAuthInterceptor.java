package com.tradelearn.server.security;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.tradelearn.server.model.User;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * WebSocket handshake interceptor that validates JWT tokens.
 *
 * The token can be provided as:
 *   1. Query parameter: /ws?token=eyJ...
 *   2. Authorization header: Bearer eyJ...
 *
 * On successful validation, the User entity is stored in the WebSocket
 * session attributes under the key "user", and userId/email under their
 * respective keys for easy access.
 *
 * If no token is provided or validation fails, the handshake is rejected.
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    /**
     * IP-based WebSocket connect rate limiter.
     * Prevents brute-force connection attempts and connection flooding.
     * Default: 10 connections per minute per IP address.
     */
    private final int connectAttemptsPerMinute;
    private final ConcurrentHashMap<String, Bucket> connectBuckets = new ConcurrentHashMap<>();

    public WebSocketAuthInterceptor(
            JwtUtil jwtUtil,
            CustomUserDetailsService userDetailsService,
            @Value("${tradelearn.ws.connect-rate-limit:10}") int connectAttemptsPerMinute) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.connectAttemptsPerMinute = connectAttemptsPerMinute;
    }

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        // ── IP-based connect rate limiting ──
        String clientIp = extractClientIp(request);
        if (!tryConsumeConnectToken(clientIp)) {
            log.warn("[WS Auth] Connect rate limited for IP: {}", clientIp);
            return false;  // Reject handshake — too many connection attempts
        }

        String token = extractToken(request);

        if (token == null || token.isBlank()) {
            log.warn("[WS Auth] No JWT token in handshake request");
            return false;   // Reject handshake
        }

        try {
            if (!jwtUtil.isValid(token)) {
                log.warn("[WS Auth] Invalid JWT token in handshake");
                return false;
            }

            // Replay attack prevention: check if this token's jti was already used
            if (!jwtUtil.checkAndRecordJti(token)) {
                log.warn("[WS Auth] JWT replay attack detected — token reuse blocked");
                return false;
            }

            String email = jwtUtil.getEmail(token);
            Long userId = jwtUtil.getUserId(token);
            String username = jwtUtil.getUsername(token);

            // Verify user still exists in DB
            User user = userDetailsService.findUserByEmail(email);

            // Store in WebSocket session attributes
            attributes.put("user", user);
            attributes.put("userId", user.getId());
            attributes.put("email", user.getEmail());
            attributes.put("username", user.getUsername());

            log.debug("[WS Auth] Handshake authenticated: userId={}, username={}", userId, username);
            return true;

        } catch (Exception e) {
            log.warn("[WS Auth] Handshake auth failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @Nullable Exception exception
    ) {
        // No-op
    }

    /**
     * Extract JWT token from query parameter or Authorization header.
     */
    private String extractToken(ServerHttpRequest request) {
        // 1. Try query parameter: ?token=eyJ...
        try {
            List<String> tokenParams = UriComponentsBuilder.fromUri(request.getURI())
                    .build()
                    .getQueryParams()
                    .get("token");
            if (tokenParams != null && !tokenParams.isEmpty()) {
                return tokenParams.get(0);
            }
        } catch (Exception e) {
            log.debug("[WS Auth] Could not parse query params: {}", e.getMessage());
        }

        // 2. Try Authorization header
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }

        // 3. Try from request headers directly
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }

        return null;
    }

    // ==================== CONNECT RATE LIMITING ====================

    /**
     * Try to consume a connect token for the given IP address.
     * Uses Bucket4j token-bucket: X connections/minute per IP.
     */
    private boolean tryConsumeConnectToken(String clientIp) {
        Bucket bucket = connectBuckets.computeIfAbsent(clientIp, k -> {
            Bandwidth limit = Bandwidth.builder()
                    .capacity(connectAttemptsPerMinute)
                    .refillGreedy(connectAttemptsPerMinute, Duration.ofMinutes(1))
                    .build();
            return Bucket.builder().addLimit(limit).build();
        });
        return bucket.tryConsume(1);
    }

    /**
     * Extract the client IP from the request, respecting X-Forwarded-For
     * headers for clients behind reverse proxies / load balancers.
     */
    private String extractClientIp(ServerHttpRequest request) {
        // Check X-Forwarded-For (first IP is the original client)
        List<String> forwarded = request.getHeaders().get("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            String first = forwarded.get(0).split(",")[0].trim();
            if (!first.isBlank()) return first;
        }

        // Check X-Real-IP
        List<String> realIp = request.getHeaders().get("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.get(0).trim();
        }

        // Fallback to remote address
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }

        return "unknown";
    }
}
