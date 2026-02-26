package com.tradelearn.server.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradelearn.server.util.GameLogger;

/**
 * Centralised room state manager — cluster-safe via {@link RedisRoomStore}.
 *
 * <h3>Architecture (hybrid local + Redis)</h3>
 *
 * <b>Redis (shared across all instances):</b>
 * <ul>
 *   <li>Room metadata (creator, phase, creation time)</li>
 *   <li>Connected-player set (who has joined)</li>
 *   <li>Disconnected-player set (grace period tracking)</li>
 *   <li>Ready-up counter</li>
 *   <li>Scheduler ownership (single-instance guarantee)</li>
 *   <li>Room index (all live game IDs)</li>
 * </ul>
 *
 * <b>JVM-local (per-instance, not shareable):</b>
 * <ul>
 *   <li>WebSocket session maps (sessionId ↔ userId ↔ gameId)</li>
 *   <li>{@link ScheduledFuture} handles for candle schedulers</li>
 *   <li>{@link ScheduledFuture} handles for reconnection grace timers</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * Local maps are {@link ConcurrentHashMap}. Redis operations use
 * Lua scripts for atomicity. No synchronized blocks.
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   createRoom()  → match created   (MatchService)
 *   joinRoom()    → opponent joins   (MatchService)
 *   startGame()   → scheduler begins (MatchSchedulerService)
 *   endGame()     → game finishes / abandoned
 * </pre>
 */
@Service
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);
    private static final int MAX_PLAYERS = 2;

    // ===================== PHASE ENUM =====================

    public enum RoomPhase {
        WAITING,    // created, waiting for opponent
        STARTING,   // opponent joined, candles loading
        ACTIVE,     // game in progress, candles streaming
        FINISHED,   // game ended normally
        ABANDONED   // player disconnected
    }

    // ===================== DEPENDENCIES =====================

    private final ResilientRedisRoomStore store;

    // ── JVM-local state (not shareable across instances) ──

    /** WebSocket sessionId → gameId */
    private final ConcurrentHashMap<String, Long> sessionToGame = new ConcurrentHashMap<>();

    /** WebSocket sessionId → userId */
    private final ConcurrentHashMap<String, Long> sessionToUser = new ConcurrentHashMap<>();

    /** gameId → { sessionId → userId } — local sessions per game */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Long>> gameSessions = new ConcurrentHashMap<>();

    /** gameId → ScheduledFuture (candle tick) — only on the owner instance */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> schedulerHandles = new ConcurrentHashMap<>();

    /** gameId → { userId → ScheduledFuture } — reconnection timers */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, ScheduledFuture<?>>> reconnectTimers = new ConcurrentHashMap<>();

    public RoomManager(ResilientRedisRoomStore store) {
        this.store = store;
    }

    // ===================== ROOM LIFECYCLE =====================

    /**
     * Create a new room when a match is created.
     * Uses Redis SETNX (via Lua) — only succeeds once per gameId,
     * preventing duplicate rooms across all instances.
     */
    public void createRoom(long gameId, long creatorId) {
        boolean created = store.createRoom(gameId, creatorId);

        if (created) {
            GameLogger.logRoomCreated(log, gameId, creatorId);
            GameLogger.logDiagnosticSnapshot(log, "Room Created", Map.of(
                "gameId", gameId,
                "creatorId", creatorId,
                "totalRooms", store.totalRoomCount(),
                "instance", store.getInstanceId()
            ));
        } else {
            log.warn("[Room {}] Already exists in Redis, ignoring create from user {}", gameId, creatorId);
        }
    }

    /**
     * Record that a player (opponent) has joined the room.
     * Atomically adds the player and transitions phase to STARTING.
     *
     * @throws IllegalStateException    if room is full
     * @throws IllegalArgumentException if room does not exist
     */
    public void joinRoom(long gameId, long userId) {
        int result = store.joinRoom(gameId, userId);

        if (result == -1) {
            throw new IllegalArgumentException("Room " + gameId + " does not exist in Redis");
        }
        if (result == -2) {
            int count = store.getPlayerCount(gameId);
            GameLogger.logGameCannotStart(log, gameId, "Room is full", Map.of(
                "currentPlayers", count,
                "maxPlayers", MAX_PLAYERS,
                "attemptingUserId", userId
            ));
            throw new IllegalStateException("Room " + gameId + " is full (max " + MAX_PLAYERS + " players)");
        }

        String phase = store.getPhase(gameId);
        GameLogger.logPlayerJoined(log, gameId, userId, result, phase != null ? phase : "STARTING");

        GameLogger.logDiagnosticSnapshot(log, "After Player Join", Map.of(
            "gameId", gameId,
            "userId", userId,
            "roomSize", result,
            "playerIds", store.getConnectedPlayers(gameId),
            "phase", phase != null ? phase : "STARTING",
            "isFull", result >= MAX_PLAYERS
        ));
    }

    /**
     * Transition the room to ACTIVE and store the scheduler handle.
     * Called by MatchSchedulerService after candles load.
     */
    public void startGame(long gameId, ScheduledFuture<?> scheduler) {
        String oldPhase = store.getPhase(gameId);
        schedulerHandles.put(gameId, scheduler);
        store.setPhase(gameId, "ACTIVE");

        int playerCount = store.getPlayerCount(gameId);
        GameLogger.logGameStateTransition(log, gameId,
                oldPhase != null ? oldPhase : "UNKNOWN", "ACTIVE", playerCount);
        GameLogger.logIntervalCreated(log, gameId, 5);

        GameLogger.logDiagnosticSnapshot(log, "Game Started", Map.of(
            "gameId", gameId,
            "playerIds", store.getConnectedPlayers(gameId),
            "roomSize", playerCount,
            "phase", "ACTIVE",
            "hasScheduler", true,
            "instance", store.getInstanceId()
        ));
    }

    /**
     * End a game: cancel local scheduler, set terminal phase in Redis,
     * clean up all local + Redis state.
     *
     * <p>Idempotent — safe to call from multiple instances / threads.
     * Redis cleanup is idempotent (SET + DELETE). Local cleanup only
     * affects this instance's resources.</p>
     */
    public void endGame(long gameId, boolean abandoned) {
        String oldPhase = store.getPhase(gameId);
        String terminal = abandoned ? "ABANDONED" : "FINISHED";

        // ── Redis: set terminal phase ──
        if (oldPhase != null) {
            store.setPhase(gameId, terminal);
        }

        int playerCount = store.getPlayerCount(gameId);
        GameLogger.logGameStateTransition(log, gameId,
                oldPhase != null ? oldPhase : "UNKNOWN", terminal, playerCount);

        // ── Local: cancel scheduler if this instance owns it ──
        ScheduledFuture<?> scheduler = schedulerHandles.remove(gameId);
        if (scheduler != null && !scheduler.isCancelled()) {
            scheduler.cancel(false);
            GameLogger.logIntervalDeleted(log, gameId,
                    abandoned ? "player_disconnect" : "game_finished");
        }

        // ── Redis: release scheduler ownership ──
        store.releaseScheduler(gameId);

        GameLogger.logDiagnosticSnapshot(log, "Game Ended", Map.of(
            "gameId", gameId,
            "finalPhase", terminal,
            "abandoned", abandoned,
            "remainingPlayers", playerCount,
            "instance", store.getInstanceId()
        ));

        // ── Clean up local sessions + Redis room keys ──
        cleanupLocalSessions(gameId);
        cancelAllReconnectTimers(gameId);
        store.deleteRoom(gameId);

        GameLogger.logRoomRemoved(log, gameId, "cleanup_after_end");
    }

    // ===================== SESSION TRACKING =====================

    /**
     * Register a WebSocket session to a user + game.
     * Local session maps + Redis player set.
     */
    public void registerSession(String sessionId, long userId, long gameId) {
        // ── Local ──
        gameSessions.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
                    .put(sessionId, userId);
        sessionToGame.put(sessionId, gameId);
        sessionToUser.put(sessionId, userId);

        // ── Redis: add player to connected set (idempotent SADD) ──
        store.addPlayer(gameId, userId);

        GameLogger.logWebSocketConnected(log, sessionId, userId, gameId);

        GameLogger.logDiagnosticSnapshot(log, "Session Registered", Map.of(
            "gameId", gameId,
            "userId", userId,
            "sessionId", sessionId,
            "roomSize", store.getPlayerCount(gameId),
            "phase", String.valueOf(store.getPhase(gameId)),
            "instance", store.getInstanceId()
        ));
    }

    /**
     * Unregister a WebSocket session (disconnect).
     *
     * Removes the session from local maps. If no other local sessions
     * remain for this user + game, also removes the player from the
     * Redis connected set.
     *
     * @return disconnect info, or {@code null} if session was untracked
     */
    public DisconnectInfo unregisterSession(String sessionId) {
        Long gameId = sessionToGame.remove(sessionId);
        Long userId = sessionToUser.remove(sessionId);
        if (gameId == null || userId == null) return null;

        // ── Local: remove from game sessions ──
        ConcurrentHashMap<String, Long> sessions = gameSessions.get(gameId);
        boolean hasOtherLocalSessions = false;
        if (sessions != null) {
            sessions.remove(sessionId);
            hasOtherLocalSessions = sessions.values().contains(userId);
        }

        // ── Redis: remove player only if no other local sessions remain ──
        if (!hasOtherLocalSessions) {
            store.removePlayer(gameId, userId);
        }

        int remainingPlayers = store.getPlayerCount(gameId);

        GameLogger.logWebSocketDisconnected(log, sessionId, userId, gameId, remainingPlayers);
        GameLogger.logPlayerLeft(log, gameId, userId, remainingPlayers, "websocket_disconnect");

        String phase = store.getPhase(gameId);
        GameLogger.logDiagnosticSnapshot(log, "Session Disconnected", Map.of(
            "gameId", gameId,
            "userId", userId,
            "sessionId", sessionId,
            "remainingPlayers", remainingPlayers,
            "remainingPlayerIds", store.getConnectedPlayers(gameId),
            "phase", phase != null ? phase : "UNKNOWN",
            "instance", store.getInstanceId()
        ));

        return new DisconnectInfo(gameId, userId, remainingPlayers);
    }

    /** Info returned after a session disconnects. */
    public record DisconnectInfo(long gameId, long userId, int remainingPlayers) {}

    // ===================== READY-UP =====================

    /**
     * Increment the ready counter (distributed via Redis).
     *
     * @return {@code true} if all players are now ready
     */
    public boolean markReady(long gameId) {
        int count = store.incrementReady(gameId);
        log.debug("[Room {}] Ready count: {}/{}", gameId, count, MAX_PLAYERS);
        return count >= MAX_PLAYERS;
    }

    // ===================== RECONNECTION GRACE PERIOD =====================

    /** Mark a player as disconnected in Redis (cluster-visible). */
    public void markDisconnected(long gameId, long userId) {
        store.markDisconnected(gameId, userId);
    }

    /** Check if a player is in the disconnected (grace) state. */
    public boolean isDisconnected(long gameId, long userId) {
        return store.isDisconnected(gameId, userId);
    }

    /** Clear the disconnected flag for a player (they reconnected). */
    public void clearDisconnected(long gameId, long userId) {
        store.clearDisconnected(gameId, userId);
    }

    /** Store a local reconnection grace timer for a player. */
    public void setReconnectTimer(long gameId, long userId, ScheduledFuture<?> timer) {
        ConcurrentHashMap<Long, ScheduledFuture<?>> timers =
                reconnectTimers.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>());
        ScheduledFuture<?> old = timers.put(userId, timer);
        if (old != null) old.cancel(false);
    }

    /** Remove and return a reconnection timer (for cancellation). */
    public ScheduledFuture<?> removeReconnectTimer(long gameId, long userId) {
        ConcurrentHashMap<Long, ScheduledFuture<?>> timers = reconnectTimers.get(gameId);
        return timers != null ? timers.remove(userId) : null;
    }

    /** Cancel all reconnection timers for a game (local + Redis disconnected set). */
    public void cancelAllReconnectTimers(long gameId) {
        ConcurrentHashMap<Long, ScheduledFuture<?>> timers = reconnectTimers.remove(gameId);
        if (timers != null) {
            timers.values().forEach(f -> f.cancel(false));
        }
        // Clear all disconnected flags in Redis for this game
        for (Long uid : store.getDisconnectedPlayers(gameId)) {
            store.clearDisconnected(gameId, uid);
        }
    }

    // ===================== SCHEDULER OWNERSHIP =====================

    /**
     * Try to claim scheduler ownership for this instance (Redis SETNX).
     * Only the owner instance should run the candle tick.
     */
    public boolean tryClaimScheduler(long gameId) {
        return store.tryClaimScheduler(gameId);
    }

    /** Refresh scheduler ownership TTL (called from tick). */
    public void refreshSchedulerOwnership(long gameId) {
        store.refreshSchedulerOwnership(gameId);
    }

    /** Release scheduler ownership (called on stop/end). */
    public void releaseScheduler(long gameId) {
        store.releaseScheduler(gameId);
    }

    // ===================== QUERIES =====================

    /** Check if a room exists in Redis. */
    public boolean hasRoom(long gameId) {
        return store.roomExists(gameId);
    }

    /** Get the room phase, or {@code null} if room does not exist. */
    public RoomPhase getPhase(long gameId) {
        String p = store.getPhase(gameId);
        if (p == null) return null;
        try {
            return RoomPhase.valueOf(p);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Get the connected player set from Redis. */
    public Set<Long> getConnectedPlayers(long gameId) {
        return store.getConnectedPlayers(gameId);
    }

    /** Get the player count from Redis. */
    public int getPlayerCount(long gameId) {
        return store.getPlayerCount(gameId);
    }

    /** Check if the room is full (≥ 2 players). */
    public boolean isRoomFull(long gameId) {
        return store.isFull(gameId);
    }

    /** Get the local scheduler handle for a game (this instance only). */
    public ScheduledFuture<?> getScheduler(long gameId) {
        return schedulerHandles.get(gameId);
    }

    /** Check if this instance has a running scheduler for a game. */
    public boolean isSchedulerRunning(long gameId) {
        ScheduledFuture<?> f = schedulerHandles.get(gameId);
        return f != null && !f.isCancelled();
    }

    /** Count rooms with ACTIVE phase (Redis scan — for Micrometer). */
    public int activeRoomCount() {
        return store.activeRoomCount();
    }

    /** Total rooms across all phases. */
    public int totalRoomCount() {
        return store.totalRoomCount();
    }

    /** All rooms as diagnostic snapshots (from Redis). */
    public Collection<Map<String, Object>> allRoomSnapshots() {
        return store.allGameIds().stream()
                .map(store::snapshot)
                .filter(s -> s != null)
                .toList();
    }

    /** Get a diagnostic snapshot for a single room, or {@code null}. */
    public Map<String, Object> getRoomSnapshot(long gameId) {
        return store.snapshot(gameId);
    }

    /** Get userId for a session (local lookup). */
    public Long getUserForSession(String sessionId) {
        return sessionToUser.get(sessionId);
    }

    /** Get gameId for a session (local lookup). */
    public Long getGameForSession(String sessionId) {
        return sessionToGame.get(sessionId);
    }

    /** Active WebSocket session count on this instance. */
    public int activeSessionCount() {
        return sessionToUser.size();
    }

    // ===================== INTERNAL =====================

    /**
     * Remove all local session mappings for a game.
     */
    private void cleanupLocalSessions(long gameId) {
        sessionToGame.entrySet().removeIf(e -> e.getValue().equals(gameId));
        ConcurrentHashMap<String, Long> sessions = gameSessions.remove(gameId);
        if (sessions != null) {
            for (String sid : sessions.keySet()) {
                sessionToUser.remove(sid);
            }
        }
    }
}
