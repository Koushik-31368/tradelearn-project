package com.tradelearn.server.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradelearn.server.socket.GameBroadcaster;

import jakarta.annotation.PostConstruct;

/**
 * Manages safe game freezing during partial infrastructure failures.
 *
 * <h3>What "freeze" means</h3>
 * <ul>
 *   <li>Candle progression stops (scheduler paused, not cancelled).</li>
 *   <li>Trades are rejected with a "system-pause" message.</li>
 *   <li>Clients receive a {@code system-pause} WebSocket event with
 *       a reason and estimated recovery time.</li>
 *   <li>The game's position snapshots and candle index are preserved
 *       exactly as they were at freeze time.</li>
 * </ul>
 *
 * <h3>What "unfreeze" means</h3>
 * <ul>
 *   <li>Clients receive a {@code system-resume} WebSocket event.</li>
 *   <li>Candle progression resumes from the exact index where it stopped.</li>
 *   <li>Trades are re-enabled.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * {@link ConcurrentHashMap} for frozen game tracking. Freeze/unfreeze
 * operations are idempotent.
 */
@Service
public class GameFreezeService {

    private static final Logger log = LoggerFactory.getLogger(GameFreezeService.class);

    /** Set of currently frozen gameIds. */
    private final Set<Long> frozenGames = ConcurrentHashMap.newKeySet();

    /** Reason for each game's freeze (for client display). */
    private final ConcurrentHashMap<Long, String> freezeReasons = new ConcurrentHashMap<>();

    /** Timestamp when each game was frozen (for metrics). */
    private final ConcurrentHashMap<Long, Long> freezeTimestamps = new ConcurrentHashMap<>();

    private final GameBroadcaster broadcaster;
    private final RoomManager roomManager;
    private final GracefulDegradationManager degradationManager;

    public GameFreezeService(GameBroadcaster broadcaster,
                             RoomManager roomManager,
                             GracefulDegradationManager degradationManager) {
        this.broadcaster = broadcaster;
        this.roomManager = roomManager;
        this.degradationManager = degradationManager;
    }

    @PostConstruct
    void init() {
        // Wire bidirectional reference
        degradationManager.setFreezeService(this);
        log.info("[GameFreeze] Service initialized");
    }

    // ==================== FREEZE ====================

    /**
     * Freeze a single game. Idempotent — no-op if already frozen.
     *
     * @param gameId the game to freeze
     * @param reason human-readable reason (sent to clients)
     */
    public void freezeGame(long gameId, String reason) {
        if (!frozenGames.add(gameId)) return; // already frozen

        freezeReasons.put(gameId, reason);
        freezeTimestamps.put(gameId, System.currentTimeMillis());

        log.warn("[GameFreeze] Game {} FROZEN: {}", gameId, reason);

        // Notify connected clients
        try {
            broadcaster.sendToGame(gameId, "system-pause", Map.of(
                    "gameId", gameId,
                    "reason", reason,
                    "message", "Game temporarily paused due to a system issue. " +
                               "Your positions are safe. The game will resume automatically.",
                    "frozenAt", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.warn("[GameFreeze] Failed to notify clients for game {}: {}", gameId, e.getMessage());
        }
    }

    /**
     * Freeze ALL active games. Called by {@link GracefulDegradationManager}
     * during critical failures.
     *
     * @param reason the global reason
     */
    public void freezeAllGames(String reason) {
        log.warn("[GameFreeze] Freezing ALL active games: {}", reason);

        // Get all rooms known to be ACTIVE
        try {
            Set<Long> allGameIds = roomManager.getConnectedPlayers(0) != null
                    ? ConcurrentHashMap.newKeySet() : ConcurrentHashMap.newKeySet();

            // Use RoomManager to get all game IDs from stored sessions
            // Since RoomManager doesn't directly expose allGameIds, we'll
            // use the store's allGameIds method via the room snapshots
            for (Map<String, Object> snapshot : roomManager.allRoomSnapshots()) {
                Object gidObj = snapshot.get("gameId");
                if (gidObj instanceof Number gid) {
                    String phase = (String) snapshot.get("phase");
                    if ("ACTIVE".equals(phase) || "STARTING".equals(phase)) {
                        freezeGame(gid.longValue(), reason);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[GameFreeze] Failed to enumerate games for global freeze: {}", e.getMessage());
        }
    }

    // ==================== UNFREEZE ====================

    /**
     * Unfreeze a single game. Idempotent — no-op if not frozen.
     *
     * @param gameId the game to unfreeze
     */
    public void unfreezeGame(long gameId) {
        if (!frozenGames.remove(gameId)) return; // not frozen

        String reason = freezeReasons.remove(gameId);
        Long frozenAt = freezeTimestamps.remove(gameId);
        long frozenDurationMs = frozenAt != null ? System.currentTimeMillis() - frozenAt : 0;

        log.info("[GameFreeze] Game {} UNFROZEN after {}ms (was: {})",
                gameId, frozenDurationMs, reason);

        // Notify connected clients
        try {
            broadcaster.sendToGame(gameId, "system-resume", Map.of(
                    "gameId", gameId,
                    "message", "Game resumed! You can continue trading.",
                    "frozenDurationMs", frozenDurationMs,
                    "resumedAt", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.warn("[GameFreeze] Failed to notify resume for game {}: {}", gameId, e.getMessage());
        }
    }

    /**
     * Unfreeze ALL games. Called by {@link GracefulDegradationManager}
     * when the system returns to NORMAL.
     */
    public void unfreezeAllGames() {
        log.info("[GameFreeze] Unfreezing ALL games ({} total)", frozenGames.size());
        // Copy to avoid ConcurrentModificationException
        Set<Long> toUnfreeze = Set.copyOf(frozenGames);
        toUnfreeze.forEach(this::unfreezeGame);
    }

    // ==================== QUERIES ====================

    /** True if a specific game is frozen. */
    public boolean isFrozen(long gameId) {
        return frozenGames.contains(gameId);
    }

    /** Number of currently frozen games. */
    public int frozenGameCount() {
        return frozenGames.size();
    }

    /** Get the freeze reason for a game, or null. */
    public String getFreezeReason(long gameId) {
        return freezeReasons.get(gameId);
    }

    /** How long a game has been frozen (ms), or 0 if not frozen. */
    public long frozenDurationMs(long gameId) {
        Long ts = freezeTimestamps.get(gameId);
        return ts != null ? System.currentTimeMillis() - ts : 0;
    }

    /** All currently frozen game IDs (unmodifiable). */
    public Set<Long> getFrozenGameIds() {
        return Set.copyOf(frozenGames);
    }

    /** Diagnostic snapshot. */
    public Map<String, Object> diagnostics() {
        return Map.of(
                "frozenCount", frozenGames.size(),
                "frozenGameIds", Set.copyOf(frozenGames),
                "reasons", Map.copyOf(freezeReasons)
        );
    }
}
