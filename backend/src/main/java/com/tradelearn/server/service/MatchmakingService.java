package com.tradelearn.server.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradelearn.server.dto.PlayerTicket;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.socket.GameBroadcaster;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Production-grade instant event-driven matchmaking engine.
 *
 * Architecture:
 *   - {@link ConcurrentSkipListSet} for O(log n) rating-ordered storage.
 *     {@code lower()} / {@code higher()} give the nearest-rated neighbors
 *     in O(log n) without sorting the entire queue.
 *   - {@link ConcurrentHashMap} for O(1) duplicate guard, cancel, and
 *     "still-queued" verification during pair extraction.
 *   - <b>Instant matching on enqueue</b> — no polling, no @Scheduled.
 *     When a player joins, we immediately check the two nearest neighbors
 *     and pair them if both players' ELO windows accept the gap.
 *   - Redisson distributed lock per pair ensures multi-instance safety
 *     (Kubernetes horizontal scaling). Lock scope is minimal:
 *     verify-both-queued → remove-both (~1 ms). Match creation (DB)
 *     happens <b>outside</b> the lock.
 *   - Background housekeeping via ScheduledExecutorService:
 *       • <b>Expansion sweep</b> (every 10 s) re-evaluates waiting tickets
 *         whose ELO window has widened (20 s → ±200, 40 s → open).
 *       • <b>Stale cleanup</b> (every 30 s) removes tickets older than
 *         2 minutes and notifies the player via WebSocket.
 *
 * Expanding ELO window:
 *     0–20 s  → ±100 rating
 *     20–40 s → ±200 rating
 *     40+ s   → accept any opponent
 *     120+ s  → auto-expired (cleanup removes)
 *
 * Thread safety:
 *   - ConcurrentSkipListSet + ConcurrentHashMap are lock-free for reads.
 *   - Pair extraction uses Redisson RLock (tryLock 500 ms / lease 3 s)
 *     so no synchronized blocks or global bottlenecks.
 *   - No blocking DB calls inside the lock.
 *
 * Designed for 10,000+ concurrent users.
 */
@Service
public class MatchmakingService {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);

    /**
     * Rating-ordered set — {@code lower(ticket)} and {@code higher(ticket)}
     * return the nearest-rated neighbors in O(log n).
     * Ordering: rating ASC → joinTime ASC → userId ASC (total).
     */
    private final ConcurrentSkipListSet<PlayerTicket> skipList = new ConcurrentSkipListSet<>();

    /** O(1) userId → ticket lookup. Also serves as idempotent duplicate guard. */
    private final ConcurrentHashMap<Long, PlayerTicket> ticketIndex = new ConcurrentHashMap<>();

    private final MatchService matchService;
    private final GameBroadcaster broadcaster;
    private final RedissonClient redisson;

    private ScheduledExecutorService housekeeper;

    public MatchmakingService(MatchService matchService,
                              GameBroadcaster broadcaster,
                              RedissonClient redisson) {
        this.matchService = matchService;
        this.broadcaster = broadcaster;
        this.redisson = redisson;
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
        log.info("[MM] Instant event-driven matchmaking engine started");
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
     * @return the created {@link Game} if an opponent was found immediately,
     *         or {@link Optional#empty()} if the player was queued
     * @throws IllegalStateException if the player is already in the queue
     */
    public Optional<Game> enqueue(PlayerTicket ticket) {
        if (ticketIndex.putIfAbsent(ticket.getUserId(), ticket) != null) {
            throw new IllegalStateException("Already in matchmaking queue");
        }
        skipList.add(ticket);
        log.info("[MM] {} (rating {}) enqueued. Queue size: {}",
                ticket.getUserId(), ticket.getRating(), ticketIndex.size());

        return tryInstantMatch(ticket);
    }

    /**
     * Remove a player from the queue (cancel search).
     *
     * @return true if the player was in the queue and removed
     */
    public boolean dequeue(long userId) {
        PlayerTicket removed = ticketIndex.remove(userId);
        if (removed != null) {
            skipList.remove(removed);
            log.info("[MM] {} dequeued. Queue size: {}", userId, ticketIndex.size());
            return true;
        }
        return false;
    }

    /**
     * Check if a player is currently in the queue.
     */
    public boolean isQueued(long userId) {
        return ticketIndex.containsKey(userId);
    }

    /**
     * Get the current queue size.
     */
    public int queueSize() {
        return ticketIndex.size();
    }

    /**
     * Get the player's wait time in seconds, or -1 if not queued.
     */
    public long getWaitTime(long userId) {
        PlayerTicket ticket = ticketIndex.get(userId);
        if (ticket == null) return -1;
        return Duration.between(ticket.getJoinTime(), Instant.now()).getSeconds();
    }

    // ==================== INSTANT MATCHING ====================

    /**
     * Attempt to match the given ticket with the nearest-rated neighbor
     * using O(log n) SkipList lookups.
     *
     * Steps:
     *   1. {@code skipList.lower(ticket)} — nearest lower-rated player
     *   2. {@code skipList.higher(ticket)} — nearest higher-rated player
     *   3. Pick the one with the smallest rating gap that both players accept
     *   4. If found → acquire Redisson lock → verify → remove both → release lock
     *   5. Create match + notify OUTSIDE the lock
     */
    private Optional<Game> tryInstantMatch(PlayerTicket ticket) {
        // Guard: ticket may have been matched/cancelled concurrently
        if (!ticketIndex.containsKey(ticket.getUserId())) {
            return Optional.empty();
        }

        PlayerTicket lower = skipList.lower(ticket);
        PlayerTicket higher = skipList.higher(ticket);

        PlayerTicket best = pickBestMatch(ticket, lower, higher);
        if (best == null) return Optional.empty();

        return tryPair(ticket, best);
    }

    /**
     * Pick the better of the two nearest neighbors (lower/higher),
     * validating both players' ELO windows.
     *
     * @return the best match candidate, or null if neither qualifies
     */
    private PlayerTicket pickBestMatch(PlayerTicket ticket,
                                       PlayerTicket lower,
                                       PlayerTicket higher) {
        int lGap = lower != null
                ? Math.abs(ticket.getRating() - lower.getRating())
                : Integer.MAX_VALUE;
        int hGap = higher != null
                ? Math.abs(ticket.getRating() - higher.getRating())
                : Integer.MAX_VALUE;

        // Both players must accept the gap (mutual ELO window check)
        boolean lOk = lower != null
                && ticketIndex.containsKey(lower.getUserId())
                && lGap <= ticket.getAllowedRatingGap()
                && lGap <= lower.getAllowedRatingGap();

        boolean hOk = higher != null
                && ticketIndex.containsKey(higher.getUserId())
                && hGap <= ticket.getAllowedRatingGap()
                && hGap <= higher.getAllowedRatingGap();

        if (lOk && hOk) return lGap <= hGap ? lower : higher;
        if (lOk) return lower;
        if (hOk) return higher;
        return null;
    }

    /**
    * Atomically claim a pair (simple synchronized block),
    * then create the match outside the lock.
    *
    * Distributed lock logic removed for lightweight config.
     */
    private Optional<Game> tryPair(PlayerTicket a, PlayerTicket b) {
        long lo = Math.min(a.getUserId(), b.getUserId());
        long hi = Math.max(a.getUserId(), b.getUserId());

        RLock lock = redisson.getLock("matchmaking:pair:" + lo + ":" + hi);
        try {
            if (!lock.tryLock(500, 3000, TimeUnit.MILLISECONDS)) {
                log.warn("[MM] Lock timeout for pair [{}, {}]", lo, hi);
                return Optional.empty();
            }
            try {
                // ── Critical section: verify + remove (~1 ms) ──
                PlayerTicket t1 = ticketIndex.remove(lo);
                if (t1 == null) return Optional.empty();

                PlayerTicket t2 = ticketIndex.remove(hi);
                if (t2 == null) {
                    ticketIndex.put(lo, t1); // rollback first removal
                    return Optional.empty();
                }

                skipList.remove(t1);
                skipList.remove(t2);

                log.info("[MM] Paired [{} r={} vs {} r={}] gap={}",
                        lo, t1.getRating(), hi, t2.getRating(),
                        Math.abs(t1.getRating() - t2.getRating()));
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
     * Uses the weakly-consistent ConcurrentSkipListSet iterator —
     * safe for concurrent modification by enqueue/dequeue threads.
     */
    void expansionSweep() {
        try {
            int checked = 0;
            int paired = 0;
            for (PlayerTicket ticket : skipList) {
                // Stale reference — already matched or cancelled
                if (!ticketIndex.containsKey(ticket.getUserId())) {
                    skipList.remove(ticket);
                    continue;
                }
                // Only re-evaluate if window has expanded past initial ±100
                if (ticket.getAllowedRatingGap() > 100) {
                    checked++;
                    Optional<Game> result = tryInstantMatch(ticket);
                    if (result.isPresent()) paired++;
                }
            }
            if (checked > 0) {
                log.debug("[MM] Expansion sweep: checked={}, paired={}, queue={}",
                        checked, paired, ticketIndex.size());
            }
        } catch (Exception e) {
            log.error("[MM] Expansion sweep error: {}", e.getMessage(), e);
        }
    }

    /**
     * Remove tickets older than {@link PlayerTicket#MAX_QUEUE_TIME} (2 min)
     * and notify players via WebSocket. Runs every 30 seconds.
     */
    void cleanupExpired() {
        try {
            int removed = 0;
            for (PlayerTicket ticket : skipList) {
                if (ticket.isExpired()) {
                    if (ticketIndex.remove(ticket.getUserId()) != null) {
                        skipList.remove(ticket);
                        removed++;
                        broadcaster.sendToUser(ticket.getUserId(), "match-expired",
                                Map.of("message",
                                        "Search timed out after 2 minutes. Please try again."));
                        log.info("[MM] Expired: userId={}", ticket.getUserId());
                    } else {
                        skipList.remove(ticket); // stale — already removed elsewhere
                    }
                }
            }
            if (removed > 0) {
                log.info("[MM] Cleanup: removed {} expired tickets, queue={}",
                        removed, ticketIndex.size());
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
