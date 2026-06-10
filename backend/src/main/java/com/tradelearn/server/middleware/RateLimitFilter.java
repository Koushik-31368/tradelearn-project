package com.tradelearn.server.middleware;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * IP-based rate limiter using Token Bucket algorithm (Bucket4j).
 *
 * Protects the API from abuse and ensures fair resource distribution
 * across 10,000+ concurrent games:
 *
 *   - General API:      100 requests/minute per IP
 *   - Match creation:    10 requests/minute per IP
 *   - Trade placement:   60 requests/minute per IP
 *   - WebSocket upgrade: 10 requests/minute per IP
 *
 * For multi-instance deployments, rate limits are per-instance.
 * To share limits across instances, back the bucket store with Redis
 * (requires bucket4j-redis extension — future enhancement).
 *
 * Configurable via application properties:
 *   tradelearn.ratelimit.enabled=true
 *   tradelearn.ratelimit.general.rpm=100
 *   tradelearn.ratelimit.trades.rpm=60
 *   tradelearn.ratelimit.create.rpm=10
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    @Value("${tradelearn.ratelimit.enabled:true}")
    private boolean enabled;

    @Value("${tradelearn.ratelimit.general.rpm:100}")
    private int generalRpm;

    @Value("${tradelearn.ratelimit.trades.rpm:60}")
    private int tradesRpm;

    @Value("${tradelearn.ratelimit.create.rpm:10}")
    private int createRpm;

    private final ObjectMapper objectMapper;

    /** Per-IP buckets for general API access */
    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();

    /** Per-IP buckets for trade-specific endpoints */
    private final Map<String, Bucket> tradeBuckets = new ConcurrentHashMap<>();

    /** Per-IP buckets for match creation */
    private final Map<String, Bucket> createBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String ip = extractClientIp(httpRequest);
        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        Bucket bucket = resolveBucket(ip, path, method);

        if (bucket.tryConsume(1)) {
            // Add rate limit headers
            httpResponse.setHeader("X-RateLimit-Remaining",
                    String.valueOf(bucket.getAvailableTokens()));
            chain.doFilter(request, response);
        } else {
            log.warn("[Rate Limit] IP {} exceeded limit on {} {}", ip, method, path);

            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "status", 429,
                    "error", "Too Many Requests",
                    "message", "Rate limit exceeded. Please slow down.",
                    "retryAfter", "60 seconds"
            )));
        }
    }

    /**
     * Select the appropriate rate limit bucket based on the request path.
     */
    private Bucket resolveBucket(String ip, String path, String method) {
        if (path.contains("/api/match/trade") || path.contains("/api/match/") && path.contains("/trade")) {
            return tradeBuckets.computeIfAbsent(ip, k -> createBucket(tradesRpm));
        }

        if ("POST".equals(method) && path.contains("/api/match/create")) {
            return createBuckets.computeIfAbsent(ip, k -> createBucket(createRpm));
        }

        return generalBuckets.computeIfAbsent(ip, k -> createBucket(generalRpm));
    }

    private Bucket createBucket(int requestsPerMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    /**
     * Extract the real client IP, respecting X-Forwarded-For from load balancers.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first IP (the original client)
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
