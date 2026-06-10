package com.tradelearn.server.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * Per-player per-game WebSocket trade rate limiter using Bucket4j token-bucket.
 *
 * Configuration:
 *   - 5 tokens (trades) per second per player per game
 *   - Greedy refill: tokens replenish smoothly, not in bursts
 *   - No burst allowance beyond the limit
 *
 * Thread-safe via ConcurrentHashMap + Bucket4j's internal CAS atomics.
 */
@Service
public class TradeRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TradeRateLimiter.class);
    private static final int TRADES_PER_SECOND = 5;

    /** Key: "gameId:userId" → token bucket */
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static String key(long gameId, long userId) {
        return gameId + ":" + userId;
    }

    /**
     * Try to consume one trade token.
     *
     * @return true if the trade is allowed, false if rate-limited
     */
    public boolean tryConsume(long gameId, long userId) {
        Bucket bucket = buckets.computeIfAbsent(key(gameId, userId), k -> createBucket());
        return bucket.tryConsume(1);
    }

    /**
     * Evict all buckets for a game (cleanup on game end).
     */
    public void evictGame(long gameId) {
        String prefix = gameId + ":";
        buckets.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
        log.debug("[RateLimit] Evicted buckets for game={}", gameId);
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(TRADES_PER_SECOND)
                .refillGreedy(TRADES_PER_SECOND, Duration.ofSeconds(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
