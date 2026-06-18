package com.tradelearn.server.infrastructure.redis.room;

import com.tradelearn.server.infrastructure.scheduling.MatchSchedulerService;
import com.tradelearn.server.service.MatchService;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    // Optional — null when redis.enabled=false (MVP / no-Redis deployment).
    // Injected via setter so RoomManager starts without Redis.
    @Autowired(required = false)
    private ResilientRedisRoomStore store;

    // ── Local in-memory room state (used when store==null) ──
    /** gameId → phase string */
    private final ConcurrentHashMap<Long, String> localPhase = new ConcurrentHashMap<>();
    /** gameId → creatorId */
    private final ConcurrentHashMap<Long, Long> localCreator = new ConcurrentHashMap<>();
    /** gameId → set of connected userIds */
    private final ConcurrentHashMap<Long, Set<Long>> localPlayers = new ConcurrentHashMap<>();
    /** gameId → set of disconnected userIds */
    private final ConcurrentHashMap<Long, Set<Long>> localDisconnected = new ConcurrentHashMap<>();

    public RoomManager() {
        // store injected via @Autowired(required=false)
    }

    // ── JVM-local state (not shareable across instances, always present) ──

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

    // ── Helpers: delegate to Redis store or fall back to local maps ──

    private boolean storeCreateRoom(long gameId, long creatorId) {
        if (store != null) return store.createRoom(gameId, creatorId);
        return localCreator.putIfAbsent(gameId, creatorId) == null;
    }

    private int storeJoinRoom(long gameId, long userId) {
        if (store != null) return store.joinRoom(gameId, userId);
        Set<Long> players = localPlayers.computeIfAbsent(gameId, k -> ConcurrentHashMap.newKeySet());
        if (!localCreator.containsKey(gameId)) return -1;
        if (players.size() >= 2) return -2;
        players.add(userId);
        localPhase.put(gameId, "STARTING");
        return players.size();
    }

    private boolean storeRoomExists(long gameId) {
        if (store != null) return store.roomExists(gameId);
        return localCreator.containsKey(gameId);
    }

    private void storeSetPhase(long gameId, String phase) {
        if (store != null) { store.setPhase(gameId, phase); return; }
        localPhase.put(gameId, phase);
    }

    private String storeGetPhase(long gameId) {
        if (store != null) return store.getPhase(gameId);
        return localPhase.get(gameId);
    }

    private void storeAddPlayer(long gameId, long userId) {
        if (store != null) { store.addPlayer(gameId, userId); return; }
        localPlayers.computeIfAbsent(gameId, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    private void storeRemovePlayer(long gameId, long userId) {
        if (store != null) { store.removePlayer(gameId, userId); return; }
        Set<Long> p = localPlayers.get(gameId);
        if (p != null) p.remove(userId);
    }

    private Set<Long> storeGetConnectedPlayers(long gameId) {
        if (store != null) return store.getConnectedPlayers(gameId);
        Set<Long> p = localPlayers.get(gameId);
        return p != null ? Collections.unmodifiableSet(p) : Collections.emptySet();
    }

    private int storeGetPlayerCount(long gameId) {
        if (store != null) return store.getPlayerCount(gameId);
        Set<Long> p = localPlayers.get(gameId);
        return p != null ? p.size() : 0;
    }

    private boolean storeIsFull(long gameId) {
        return storeGetPlayerCount(gameId) >= 2;
    }

    private void storeMarkDisconnected(long gameId, long userId) {
        if (store != null) { store.markDisconnected(gameId, userId); return; }
        localDisconnected.computeIfAbsent(gameId, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    private void storeClearDisconnected(long gameId, long userId) {
        if (store != null) { store.clearDisconnected(gameId, userId); return; }
        Set<Long> d = localDisconnected.get(gameId);
        if (d != null) d.remove(userId);
    }

    private boolean storeIsDisconnected(long gameId, long userId) {
        if (store != null) return store.isDisconnected(gameId, userId);
        Set<Long> d = localDisconnected.get(gameId);
        return d != null && d.contains(userId);
    }

    private Set<Long> storeGetDisconnectedPlayers(long gameId) {
        if (store != null) return store.getDisconnectedPlayers(gameId);
        Set<Long> d = localDisconnected.get(gameId);
        return d != null ? Collections.unmodifiableSet(d) : Collections.emptySet();
    }

    private int storeIncrementReady(long gameId) {
        if (store != null) return store.incrementReady(gameId);
        return 0; // local fallback: skip ready-up sync
    }

    private boolean storeTryClaimScheduler(long gameId) {
        if (store != null) return store.tryClaimScheduler(gameId);
        return true; // single instance: always claim
    }

    private void storeRefreshSchedulerOwnership(long gameId) {
        if (store != null) store.refreshSchedulerOwnership(gameId);
    }

    private void storeReleaseScheduler(long gameId) {
        if (store != null) store.releaseScheduler(gameId);
    }

    private boolean storeHasSchedulerOwner(long gameId) {
        if (store != null) return store.hasSchedulerOwner(gameId);
        return schedulerHandles.containsKey(gameId);
    }

    private void storeDeleteRoom(long gameId) {
        if (store != null) { store.deleteRoom(gameId); }
        localCreator.remove(gameId);
        localPhase.remove(gameId);
        localPlayers.remove(gameId);
        localDisconnected.remove(gameId);
    }

    private String storeGetInstanceId() {
        if (store != null) return store.getInstanceId();
        return "local";
    }

    private int storeActiveRoomCount() {
        if (store != null) return store.activeRoomCount();
        return (int) localPhase.values().stream().filter("ACTIVE"::equals).count();
    }

    private int storeTotalRoomCount() {
        if (store != null) return store.totalRoomCount();
        return localCreator.size();
    }

    private Set<Long> storeAllGameIds() {
        if (store != null) return store.allGameIds();
        return Collections.unmodifiableSet(localCreator.keySet());
    }

    private Map<String, Object> storeSnapshot(long gameId) {
        if (store != null) return store.snapshot(gameId);
        Map<String, Object> snap = new HashMap<>();
        snap.put("gameId", gameId);
        snap.put("phase", localPhase.getOrDefault(gameId, "UNKNOWN"));
        snap.put("creatorId", localCreator.getOrDefault(gameId, -1L));
        snap.put("playerCount", storeGetPlayerCount(gameId));
        snap.put("fallback", true);
        return snap;
    }


    // ===================== ROOM LIFECYCLE =====================

    /**
     * Create a new room when a match is created.
     * Uses Redis SETNX (via Lua) — only succeeds once per gameId,
     * preventing duplicate rooms across all instances.
     */
    public void createRoom(long gameId, long creatorId) {
        boolean created = storeCreateRoom(gameId, creatorId);

        if (created) {
            GameLogger.logRoomCreated(log, gameId, creatorId);
            GameLogger.logDiagnosticSnapshot(log, "Room Created", Map.of(
                "gameId", gameId,
                "creatorId", creatorId,
                "totalRooms", storeTotalRoomCount(),
                "instance", storeGetInstanceId()
            ));
        } else {
            log.warn("[Room {}] Already exists, ignoring create from user {}", gameId, creatorId);
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
        int result = storeJoinRoom(gameId, userId);

        if (result == -1) {
            throw new IllegalArgumentException("Room " + gameId + " does not exist");
        }
        if (result == -2) {
            int count = storeGetPlayerCount(gameId);
            GameLogger.logGameCannotStart(log, gameId, "Room is full", Map.of(
                "currentPlayers", count,
                "maxPlayers", MAX_PLAYERS,
                "attemptingUserId", userId
            ));
            throw new IllegalStateException("Room " + gameId + " is full (max " + MAX_PLAYERS + " players)");
        }

        String phase = storeGetPhase(gameId);
        GameLogger.logPlayerJoined(log, gameId, userId, result, phase != null ? phase : "STARTING");

        GameLogger.logDiagnosticSnapshot(log, "After Player Join", Map.of(
            "gameId", gameId,
            "userId", userId,
            "roomSize", result,
            "playerIds", storeGetConnectedPlayers(gameId),
            "phase", phase != null ? phase : "STARTING",
            "isFull", result >= MAX_PLAYERS
        ));
    }

    /**
     * Transition the room to ACTIVE and store the scheduler handle.
     * Called by MatchSchedulerService after candles load.
     */
    public void startGame(long gameId, ScheduledFuture<?> scheduler) {
        String oldPhase = storeGetPhase(gameId);
        schedulerHandles.put(gameId, scheduler);
        storeSetPhase(gameId, "ACTIVE");

        int playerCount = storeGetPlayerCount(gameId);
        GameLogger.logGameStateTransition(log, gameId,
                oldPhase != null ? oldPhase : "UNKNOWN", "ACTIVE", playerCount);
        GameLogger.logIntervalCreated(log, gameId, 5);

        GameLogger.logDiagnosticSnapshot(log, "Game Started", Map.of(
            "gameId", gameId,
            "playerIds", storeGetConnectedPlayers(gameId),
            "roomSize", playerCount,
            "phase", "ACTIVE",
            "hasScheduler", true,
            "instance", storeGetInstanceId()
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
        String oldPhase = storeGetPhase(gameId);
        String terminal = abandoned ? "ABANDONED" : "FINISHED";

        if (oldPhase != null) {
            storeSetPhase(gameId, terminal);
        }

        int playerCount = storeGetPlayerCount(gameId);
        GameLogger.logGameStateTransition(log, gameId,
                oldPhase != null ? oldPhase : "UNKNOWN", terminal, playerCount);

        ScheduledFuture<?> scheduler = schedulerHandles.remove(gameId);
        if (scheduler != null && !scheduler.isCancelled()) {
            scheduler.cancel(false);
            GameLogger.logIntervalDeleted(log, gameId,
                    abandoned ? "player_disconnect" : "game_finished");
        }

        storeReleaseScheduler(gameId);

        GameLogger.logDiagnosticSnapshot(log, "Game Ended", Map.of(
            "gameId", gameId,
            "finalPhase", terminal,
            "abandoned", abandoned,
            "remainingPlayers", playerCount,
            "instance", storeGetInstanceId()
        ));

        cleanupLocalSessions(gameId);
        cancelAllReconnectTimers(gameId);
        storeDeleteRoom(gameId);

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
        storeAddPlayer(gameId, userId);

        GameLogger.logWebSocketConnected(log, sessionId, userId, gameId);

        GameLogger.logDiagnosticSnapshot(log, "Session Registered", Map.of(
            "gameId", gameId,
            "userId", userId,
            "sessionId", sessionId,
            "roomSize", storeGetPlayerCount(gameId),
            "phase", String.valueOf(storeGetPhase(gameId)),
            "instance", storeGetInstanceId()
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
            storeRemovePlayer(gameId, userId);
        }

        int remainingPlayers = storeGetPlayerCount(gameId);

        GameLogger.logWebSocketDisconnected(log, sessionId, userId, gameId, remainingPlayers);
        GameLogger.logPlayerLeft(log, gameId, userId, remainingPlayers, "websocket_disconnect");

        String phase = storeGetPhase(gameId);
        GameLogger.logDiagnosticSnapshot(log, "Session Disconnected", Map.of(
            "gameId", gameId,
            "userId", userId,
            "sessionId", sessionId,
            "remainingPlayers", remainingPlayers,
            "remainingPlayerIds", storeGetConnectedPlayers(gameId),
            "phase", phase != null ? phase : "UNKNOWN",
            "instance", storeGetInstanceId()
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
        int count = storeIncrementReady(gameId);
        log.debug("[Room {}] Ready count: {}/{}", gameId, count, MAX_PLAYERS);
        return count >= MAX_PLAYERS;
    }

    // ===================== RECONNECTION GRACE PERIOD =====================

    /** Mark a player as disconnected in Redis (cluster-visible). */
    public void markDisconnected(long gameId, long userId) {
        storeMarkDisconnected(gameId, userId);
    }

    public boolean isDisconnected(long gameId, long userId) {
        return storeIsDisconnected(gameId, userId);
    }

    public void clearDisconnected(long gameId, long userId) {
        storeClearDisconnected(gameId, userId);
    }

    public void setReconnectTimer(long gameId, long userId, ScheduledFuture<?> timer) {
        ConcurrentHashMap<Long, ScheduledFuture<?>> timers =
                reconnectTimers.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>());
        ScheduledFuture<?> old = timers.put(userId, timer);
        if (old != null) old.cancel(false);
    }

    public ScheduledFuture<?> removeReconnectTimer(long gameId, long userId) {
        ConcurrentHashMap<Long, ScheduledFuture<?>> timers = reconnectTimers.get(gameId);
        return timers != null ? timers.remove(userId) : null;
    }

    public void cancelAllReconnectTimers(long gameId) {
        ConcurrentHashMap<Long, ScheduledFuture<?>> timers = reconnectTimers.remove(gameId);
        if (timers != null) {
            timers.values().forEach(f -> f.cancel(false));
        }
        for (Long uid : storeGetDisconnectedPlayers(gameId)) {
            storeClearDisconnected(gameId, uid);
        }
    }

    // ===================== SCHEDULER OWNERSHIP =====================

    /**
     * Try to claim scheduler ownership for this instance (Redis SETNX).
     * Only the owner instance should run the candle tick.
     */
    public boolean tryClaimScheduler(long gameId) {
        return storeTryClaimScheduler(gameId);
    }

    public void refreshSchedulerOwnership(long gameId) {
        storeRefreshSchedulerOwnership(gameId);
    }

    public void releaseScheduler(long gameId) {
        storeReleaseScheduler(gameId);
    }

    public boolean hasSchedulerOwner(long gameId) {
        return storeHasSchedulerOwner(gameId);
    }

    // ===================== QUERIES =====================

    public boolean hasRoom(long gameId) {
        return storeRoomExists(gameId);
    }

    public RoomPhase getPhase(long gameId) {
        String p = storeGetPhase(gameId);
        if (p == null) return null;
        try {
            return RoomPhase.valueOf(p);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Set<Long> getConnectedPlayers(long gameId) {
        return storeGetConnectedPlayers(gameId);
    }

    public int getPlayerCount(long gameId) {
        return storeGetPlayerCount(gameId);
    }

    public boolean isRoomFull(long gameId) {
        return storeIsFull(gameId);
    }

    public ScheduledFuture<?> getScheduler(long gameId) {
        return schedulerHandles.get(gameId);
    }

    public boolean isSchedulerRunning(long gameId) {
        ScheduledFuture<?> f = schedulerHandles.get(gameId);
        return f != null && !f.isCancelled();
    }

    public int activeRoomCount() {
        return storeActiveRoomCount();
    }

    public int totalRoomCount() {
        return storeTotalRoomCount();
    }

    public Collection<Map<String, Object>> allRoomSnapshots() {
        return storeAllGameIds().stream()
                .filter(id -> id != null)
                .map(id -> storeSnapshot(id))
                .filter(s -> s != null)
                .toList();
    }

    public Map<String, Object> getRoomSnapshot(long gameId) {
        return storeSnapshot(gameId);
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
