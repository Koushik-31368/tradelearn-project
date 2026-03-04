package com.tradelearn.server.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.tradelearn.server.dto.PlayerTicket;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.socket.GameBroadcaster;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Production-grade distributed matchmaking engine backed by Redis.
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li>Redis ZSET ({@code matchmaking:queue}) for O(log n) rating-ordered storage.
 *       {@code ZRANGEBYSCORE} finds nearest-rated neighbors within the ELO window.</li>
 *   <li>Redis Hash ({@code matchmaking:ticket:{userId}}) for per-player metadata
 *       (username, rating, joinTime). TTL auto-expires stale tickets.</li>
 *   <li><b>Instant matching on enqueue</b> — no polling, no @Scheduled.
 *       When a player joins, we immediately search the ZSET for compatible neighbors.</li>
 *   <li>Redisson distributed lock per pair ensures multi-instance safety.
 *       Lock scope is minimal: Lua-verify-both → Lua-remove-both (~1 ms).
 *       Match creation (DB) happens <b>outside</b> the lock.</li>
 *   <li>Background housekeeping via ScheduledExecutorService:
 *       <ul>
 *         <li><b>Expansion sweep</b> (every 10 s) re-evaluates waiting tickets
 *             whose ELO window has widened (20 s → ±200, 40 s → open).</li>
 *         <li><b>Stale cleanup</b> (every 30 s) removes tickets older than
 *             2 minutes and notifies the player via WebSocket.</li>
 *       </ul></li>
 * </ul>
 *
 * <h3>Expanding ELO window</h3>
 * <pre>
 *     0–20 s  → ±100 rating
 *     20–40 s → ±200 rating
 *     40+ s   → accept any opponent
 *     120+ s  → auto-expired (cleanup removes)
 * </pre>
 *
 * <h3>Redis keys introduced</h3>
 * <pre>
 *     matchmaking:queue                → ZSET  (score=rating, member=userId)
 *     matchmaking:ticket:{userId}      → Hash  {userId, username, rating, joinTime}  TTL 180s
 *     matchmaking:pair:{lo}:{hi}       → Redisson RLock (existing)
 * </pre>
 *
 * <h3>Cluster-safe</h3>
 * All state lives in Redis. Any instance can enqueue, dequeue, or match players.
 * Redisson distributed lock prevents double-matching.
 * Lua scripts ensure atomic multi-key operations.
 *
 * Designed for 10,000+ concurrent users across 5+ backend instances.
 */
@Service
public class MatchmakingService implements QueueSizeProvider {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);

    // ── Redis key schema ──
    private static final String QUEUE_KEY = "matchmaking:queue";
    private static final String TICKET_PREFIX = "matchmaking:ticket:";
    private static final Duration TICKET_TTL = Duration.ofSeconds(180); // 3 min safety net

    // ── ELO window constants ──
    private static final int INITIAL_GAP = 100;
    private static final int EXPANDED_GAP = 200;
    private static final long EXPAND_THRESHOLD_SEC = 20;
    private static final long WIDE_OPEN_THRESHOLD_SEC = 40;
    private static final long EXPIRE_THRESHOLD_SEC = 120;
    private static final int NEIGHBOR_SCAN_LIMIT = 20;

    // ── Lua: atomic enqueue (ZSET + Hash). Returns 0 if already queued. ──
    private static final String ENQUEUE_LUA = """
            if redis.call('EXISTS', KEYS[2]) == 1 then
              return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
            redis.call('HSET', KEYS[2], 'userId', ARGV[1], 'username', ARGV[3], 'rating', ARGV[2], 'joinTime', ARGV[4])
            redis.call('EXPIRE', KEYS[2], ARGV[5])
            return 1
            """;

    // ── Lua: atomic dequeue (ZSET + Hash) ──
    private static final String DEQUEUE_LUA = """
            local removed = redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('DEL', KEYS[2])
            return removed
            """;

    // ── Lua: atomic pair removal — verify both exist, remove both ──
    private static final String PAIR_REMOVE_LUA = """
            if redis.call('ZSCORE', KEYS[1], ARGV[1]) == false then
              return 0
            end
            if redis.call('ZSCORE', KEYS[1], ARGV[2]) == false then
              return 0
            end
            redis.call('ZREM', KEYS[1], ARGV[1], ARGV[2])
            redis.call('DEL', KEYS[2], KEYS[3])
            return 1
            """;

    private final MatchService matchService;
    private final GameBroadcaster broadcaster;
    private final RedissonClient redisson;
    private final StringRedisTemplate redis;

    private ScheduledExecutorService housekeeper;

    public MatchmakingService(MatchService matchService,
                              GameBroadcaster broadcaster,
                              RedissonClient redisson,
                              StringRedisTemplate redis) {
        this.matchService = matchService;
        this.broadcaster = broadcaster;
        this.redisson = redisson;
        this.redis = redis;
    }

    @PostConstruct
    void init() {
        housekeeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mm-housekeeper");
            t.setDaemon(true);
            return t;
        });
        // Re-evaluate waiting players whose ELO window has expanded
        housekeeper.scheduleWithFixedDelay(this::expansionSweep, 10, 10, TimeUnit.SECONDS);
        // Remove tickets older than 2 minutes
        housekeeper.scheduleWithFixedDelay(this::cleanupExpired, 30, 30, TimeUnit.SECONDS);
        log.info("[MM] Distributed Redis-backed matchmaking engine started");
    }

    @PreDestroy
    void shutdown() {
        if (housekeeper != null) {
            housekeeper.shutdownNow();
            log.info("[MM] Housekeeper shut down");
        }
    }

    // ==================== PUBLIC API ====================

    /**
     * Enqueue a player and attempt an instant match.
     *
     * Atomically adds the player to the Redis ZSET + ticket hash.
     * Then searches for the nearest compatible neighbor.
     *
     * @return the created {@link Game} if an opponent was found immediately,
     *         or {@link Optional#empty()} if the player was queued
     * @throws IllegalStateException if the player is already in the queue
     */
    public Optional<Game> enqueue(PlayerTicket ticket) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ENQUEUE_LUA, Long.class);
        Long result = redis.execute(script,
                List.of(QUEUE_KEY, TICKET_PREFIX + ticket.getUserId()),
                String.valueOf(ticket.getUserId()),
                String.valueOf(ticket.getRating()),
                ticket.getUsername(),
                ticket.getJoinTime().toString(),
                String.valueOf(TICKET_TTL.getSeconds()));

        if (result == null || result == 0) {
            throw new IllegalStateException("Already in matchmaking queue");
        }

        log.info("[MM] {} (rating {}) enqueued. Queue size: {}",
                ticket.getUserId(), ticket.getRating(), queueSize());

        return tryInstantMatch(ticket.getUserId(), ticket.getRating());
    }

    /**
     * Remove a player from the queue (cancel search).
     *
     * @return true if the player was in the queue and removed
     */
    public boolean dequeue(long userId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(DEQUEUE_LUA, Long.class);
        Long result = redis.execute(script,
                List.of(QUEUE_KEY, TICKET_PREFIX + userId),
                String.valueOf(userId));

        boolean removed = result != null && result > 0;
        if (removed) {
            log.info("[MM] {} dequeued. Queue size: {}", userId, queueSize());
        }
        return removed;
    }

    /**
     * Check if a player is currently in the queue.
     */
    public boolean isQueued(long userId) {
        return Boolean.TRUE.equals(redis.hasKey(TICKET_PREFIX + userId));
    }

    /**
     * Get the current queue size (global across all instances).
     */
    @Override
    public int queueSize() {
        Long size = redis.opsForZSet().zCard(QUEUE_KEY);
        return size != null ? size.intValue() : 0;
    }

    /**
     * Get the player's wait time in seconds, or -1 if not queued.
     */
    public long getWaitTime(long userId) {
        Object joinTimeObj = redis.opsForHash().get(TICKET_PREFIX + userId, "joinTime");
        if (joinTimeObj == null) return -1;
        try {
            Instant joinTime = Instant.parse(joinTimeObj.toString());
            return Duration.between(joinTime, Instant.now()).getSeconds();
        } catch (Exception e) {
            return -1;
        }
    }

    // ==================== INSTANT MATCHING ====================

    /**
     * Search the Redis ZSET for the nearest compatible neighbor using
     * ZRANGEBYSCORE with the caller's ELO window, applying mutual
     * window acceptance for each candidate.
     */
    private Optional<Game> tryInstantMatch(long userId, int rating) {
        // Guard: ticket may have been matched/cancelled concurrently
        if (!isQueued(userId)) return Optional.empty();

        int gap = computeAllowedGap(userId);
        if (gap <= 0) return Optional.empty();

        double minScore = rating - gap;
        double maxScore = rating + gap;

        Set<ZSetOperations.TypedTuple<String>> candidates =
                redis.opsForZSet().rangeByScoreWithScores(
                        QUEUE_KEY, minScore, maxScore, 0, NEIGHBOR_SCAN_LIMIT);

        if (candidates == null || candidates.isEmpty()) return Optional.empty();

        // Find the best candidate (closest rating, mutual window acceptance)
        long bestUserId = -1;
        int bestRating = 0;
        int bestGapDiff = Integer.MAX_VALUE;

        for (ZSetOperations.TypedTuple<String> tuple : candidates) {
            if (tuple.getValue() == null || tuple.getScore() == null) continue;

            long candidateId;
            try {
                candidateId = Long.parseLong(tuple.getValue());
            } catch (NumberFormatException e) {
                continue;
            }

            if (candidateId == userId) continue; // skip self

            int candidateRating = tuple.getScore().intValue();
            int ratingDiff = Math.abs(rating - candidateRating);

            // Mutual window check: candidate must also accept the gap
            int candidateGap = computeAllowedGap(candidateId);
            if (ratingDiff > candidateGap) continue;

            if (ratingDiff < bestGapDiff) {
                bestGapDiff = ratingDiff;
                bestUserId = candidateId;
                bestRating = candidateRating;
            }
        }

        if (bestUserId < 0) return Optional.empty();

        return tryPair(userId, rating, bestUserId, bestRating);
    }

    /**
     * Compute the allowed ELO gap for a player based on their wait time.
     * Reads joinTime from the Redis ticket hash.
     *
     * 0–20 s  → ±100
     * 20–40 s → ±200
     * 40+ s   → accept any
     */
    private int computeAllowedGap(long userId) {
        Object joinTimeObj = redis.opsForHash().get(TICKET_PREFIX + userId, "joinTime");
        if (joinTimeObj == null) return INITIAL_GAP;
        try {
            Instant joinTime = Instant.parse(joinTimeObj.toString());
            long waitSec = Duration.between(joinTime, Instant.now()).getSeconds();
            if (waitSec < EXPAND_THRESHOLD_SEC) return INITIAL_GAP;
            if (waitSec < WIDE_OPEN_THRESHOLD_SEC) return EXPANDED_GAP;
            return Integer.MAX_VALUE;
        } catch (Exception e) {
            return INITIAL_GAP;
        }
    }

    /**
     * Atomically claim a pair under Redisson distributed lock,
     * verify + remove both players via Lua script (atomic),
     * then create the match OUTSIDE the lock.
     *
     * Locking strategy:
     *   Key = matchmaking:pair:{minUserId}:{maxUserId}
     *   tryLock 500 ms / lease 3000 ms
     *   Inside lock: Lua script verifies both in ZSET + removes both + deletes hashes
     *   Outside lock: createAutoMatch (DB transaction)
     *
     * Race-condition free because:
     *   - Distributed lock serializes all attempts to pair the same two players
     *   - Lua script is atomic on Redis server — no TOCTOU
     *   - If either player was already matched, Lua returns 0 → skip
     */
    private Optional<Game> tryPair(long userId1, int rating1, long userId2, int rating2) {
        long lo = Math.min(userId1, userId2);
        long hi = Math.max(userId1, userId2);

        RLock lock = redisson.getLock("matchmaking:pair:" + lo + ":" + hi);
        try {
            if (!lock.tryLock(500, 3000, TimeUnit.MILLISECONDS)) {
                log.warn("[MM] Lock timeout for pair [{}, {}]", lo, hi);
                return Optional.empty();
            }
            try {
                // ── Critical section: Lua verify + remove (~1 ms) ──
                DefaultRedisScript<Long> script = new DefaultRedisScript<>(PAIR_REMOVE_LUA, Long.class);
                Long result = redis.execute(script,
                        List.of(QUEUE_KEY, TICKET_PREFIX + lo, TICKET_PREFIX + hi),
                        String.valueOf(lo),
                        String.valueOf(hi));

                if (result == null || result == 0) {
                    // One or both players already matched/dequeued
                    return Optional.empty();
                }

                log.info("[MM] Paired [{} vs {}] gap={}",
                        lo, hi, Math.abs(rating1 - rating2));
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }

        // ── Match creation OUTSIDE lock (DB transaction) ──
        try {
            Game game = matchService.createAutoMatch(lo, hi);
            notifyMatchFound(lo, hi, game);
            log.info("[MM] Match created: gameId={}, [{} vs {}]", game.getId(), lo, hi);
            return Optional.of(game);
        } catch (Exception e) {
            log.error("[MM] Match creation failed [{} vs {}]: {}",
                    lo, hi, e.getMessage(), e);
            // Players removed from queue — they must re-queue on failure
            return Optional.empty();
        }
    }

    // ==================== BACKGROUND HOUSEKEEPING ====================

    /**
     * Re-evaluate waiting tickets whose ELO window has expanded
     * (waited > 20 s). Runs every 10 seconds on a daemon thread.
     *
     * Scans the Redis ZSET for all members and checks each ticket's
     * wait time from the Redis Hash. If the window has expanded past
     * the initial ±100, attempts an instant match.
     */
    void expansionSweep() {
        try {
            Set<ZSetOperations.TypedTuple<String>> all =
                    redis.opsForZSet().rangeWithScores(QUEUE_KEY, 0, -1);
            if (all == null || all.isEmpty()) return;

            int checked = 0;
            int paired = 0;

            for (ZSetOperations.TypedTuple<String> tuple : all) {
                if (tuple.getValue() == null || tuple.getScore() == null) continue;

                long userId;
                try {
                    userId = Long.parseLong(tuple.getValue());
                } catch (NumberFormatException e) {
                    continue;
                }

                // Only re-evaluate if window has expanded past initial ±100
                int gap = computeAllowedGap(userId);
                if (gap > INITIAL_GAP) {
                    checked++;
                    int rating = tuple.getScore().intValue();
                    Optional<Game> result = tryInstantMatch(userId, rating);
                    if (result.isPresent()) paired++;
                }
            }

            if (checked > 0) {
                log.debug("[MM] Expansion sweep: checked={}, paired={}, queue={}",
                        checked, paired, queueSize());
            }
        } catch (Exception e) {
            log.error("[MM] Expansion sweep error: {}", e.getMessage(), e);
        }
    }

    /**
     * Remove tickets older than 2 minutes and notify players via WebSocket.
     * Also removes orphaned ZSET entries (ticket hash expired but ZSET member remains).
     * Runs every 30 seconds.
     */
    void cleanupExpired() {
        try {
            Set<ZSetOperations.TypedTuple<String>> all =
                    redis.opsForZSet().rangeWithScores(QUEUE_KEY, 0, -1);
            if (all == null || all.isEmpty()) return;

            int removed = 0;

            for (ZSetOperations.TypedTuple<String> tuple : all) {
                if (tuple.getValue() == null) continue;

                long userId;
                try {
                    userId = Long.parseLong(tuple.getValue());
                } catch (NumberFormatException e) {
                    // Corrupt entry — remove from ZSET
                    redis.opsForZSet().remove(QUEUE_KEY, tuple.getValue());
                    removed++;
                    continue;
                }

                Object joinTimeObj = redis.opsForHash().get(TICKET_PREFIX + userId, "joinTime");
                if (joinTimeObj == null) {
                    // Orphaned ZSET entry — ticket hash expired or missing
                    redis.opsForZSet().remove(QUEUE_KEY, tuple.getValue());
                    removed++;
                    continue;
                }

                try {
                    Instant joinTime = Instant.parse(joinTimeObj.toString());
                    long waitSec = Duration.between(joinTime, Instant.now()).getSeconds();
                    if (waitSec >= EXPIRE_THRESHOLD_SEC) {
                        // Expired — remove atomically
                        DefaultRedisScript<Long> script = new DefaultRedisScript<>(DEQUEUE_LUA, Long.class);
                        redis.execute(script,
                                List.of(QUEUE_KEY, TICKET_PREFIX + userId),
                                String.valueOf(userId));
                        removed++;

                        broadcaster.sendToUser(userId, "match-expired",
                                Map.of("message",
                                        "Search timed out after 2 minutes. Please try again."));
                        log.info("[MM] Expired: userId={}", userId);
                    }
                } catch (Exception e) {
                    // Invalid joinTime — clean up
                    redis.opsForZSet().remove(QUEUE_KEY, tuple.getValue());
                    redis.delete(TICKET_PREFIX + userId);
                    removed++;
                }
            }

            if (removed > 0) {
                log.info("[MM] Cleanup: removed {} expired tickets, queue={}",
                        removed, queueSize());
            }
        } catch (Exception e) {
            log.error("[MM] Cleanup error: {}", e.getMessage(), e);
        }
    }

    // ==================== NOTIFICATION ====================

    private void notifyMatchFound(long userId1, long userId2, Game game) {
        Map<String, Object> payload = Map.of(
                "gameId", game.getId(),
                "stockSymbol", game.getStockSymbol(),
                "durationMinutes", game.getDurationMinutes(),
                "startingBalance", game.getStartingBalance(),
                "status", "ACTIVE"
        );
        broadcaster.sendToUser(userId1, "match-found", payload);
        broadcaster.sendToUser(userId2, "match-found", payload);
    }
}
