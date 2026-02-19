package com.tradelearn.server.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradelearn.server.util.GameLogger;

/**
 * Centralised in-memory state for every active multiplayer room.
 *
 * This is the SINGLE SOURCE OF TRUTH for per-game runtime state.
 * Database (Game entity) owns persistent state; this class owns
 * ephemeral state that only matters while the game is live:
 *
 *   - Connected players (WebSocket session IDs)
 *   - Candle scheduler handle (ScheduledFuture)
 *   - Game phase (enum, fast reads without DB round-trip)
 *   - Ready-up counter (for pre-game sync)
 *
 * Thread-safety: every field in {@link Room} is either atomic or
 * backed by ConcurrentHashMap. The rooms map itself is a CHM, so
 * all operations are safe under concurrent access from WebSocket
 * threads, scheduler threads, and REST threads simultaneously.
 *
 * Lifecycle:
 *   createRoom()  → when a match is created   (MatchService.createMatch)
 *   joinRoom()    → when opponent joins        (MatchService.joinMatch)
 *   startGame()   → when candle scheduler begins (MatchSchedulerService)
 *   endGame()     → when game finishes / abandoned
 *   removeRoom()  → final cleanup (auto after endGame, or manual)
 */
@Service
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);
    private static final int MAX_PLAYERS = 2;

    // ===================== ROOM STATE =====================

    public enum RoomPhase {
        WAITING,    // created, waiting for opponent
        STARTING,   // opponent joined, candles loading
        ACTIVE,     // game in progress, candles streaming
        FINISHED,   // game ended normally
        ABANDONED   // player disconnected
    }

    /**
     * Complete in-memory state for one game room.
     * All fields are thread-safe for concurrent reads/writes.
     */
    public static class Room {
        private final long gameId;
        private final long creatorId;
        private final Instant createdAt;
        private final AtomicReference<RoomPhase> phase;

        /** Connected WebSocket session IDs → userId */
        private final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();

        /** Connected user IDs (derived from sessions, for fast lookup) */
        private final Set<Long> connectedPlayers = ConcurrentHashMap.newKeySet();

        /** The candle scheduler handle — null until game starts */
        private volatile ScheduledFuture<?> schedulerHandle;

        /** Ready-up counter for pre-game sync */
        private final AtomicInteger readyCount = new AtomicInteger(0);

        public Room(long gameId, long creatorId) {
            this.gameId = gameId;
            this.creatorId = creatorId;
            this.createdAt = Instant.now();
            this.phase = new AtomicReference<>(RoomPhase.WAITING);
        }

        // ── Getters ──

        public long getGameId()                   { return gameId; }
        public long getCreatorId()                { return creatorId; }
        public Instant getCreatedAt()             { return createdAt; }
        public RoomPhase getPhase()               { return phase.get(); }
        public int getPlayerCount()               { return connectedPlayers.size(); }
        public Set<Long> getConnectedPlayers()    { return Collections.unmodifiableSet(connectedPlayers); }
        public ScheduledFuture<?> getScheduler()  { return schedulerHandle; }
        public boolean isFull()                   { return connectedPlayers.size() >= MAX_PLAYERS; }
        public boolean isEmpty()                  { return connectedPlayers.isEmpty(); }
        public boolean hasScheduler()             { return schedulerHandle != null && !schedulerHandle.isCancelled(); }

        /**
         * Returns a read-only snapshot for diagnostics / REST endpoints.
         */
        public Map<String, Object> snapshot() {
            return Map.of(
                "gameId",           gameId,
                "creatorId",        creatorId,
                "phase",            phase.get().name(),
                "connectedPlayers", Set.copyOf(connectedPlayers),
                "playerCount",      connectedPlayers.size(),
                "hasScheduler",     hasScheduler(),
                "createdAt",        createdAt.toString()
            );
        }
    }

    // ===================== ROOMS REGISTRY =====================

    /** gameId → Room.  The ONLY map that matters. */
    private final ConcurrentHashMap<Long, Room> rooms = new ConcurrentHashMap<>();

    /** Reverse lookup: WebSocket sessionId → gameId (for disconnect handling) */
    private final ConcurrentHashMap<String, Long> sessionToGame = new ConcurrentHashMap<>();

    /** Reverse lookup: WebSocket sessionId → userId */
    private final ConcurrentHashMap<String, Long> sessionToUser = new ConcurrentHashMap<>();

    // ===================== ROOM LIFECYCLE =====================

    /**
     * Create a new room when a match is created.
     *
     * @param gameId    The database game ID
     * @param creatorId The user who created the game
     * @return The created Room, or existing room if already present
     */
    public Room createRoom(long gameId, long creatorId) {
        Room room = rooms.computeIfAbsent(gameId, id -> {
            Room newRoom = new Room(id, creatorId);
            GameLogger.logRoomCreated(log, id, creatorId);
            GameLogger.logDiagnosticSnapshot(log, "Room Created", Map.of(
                "gameId", id,
                "creatorId", creatorId,
                "totalRooms", rooms.size() + 1,
                "activeSessions", activeSessionCount()
            ));
            return newRoom;
        });

        if (room.getCreatorId() != creatorId) {
            log.warn("[Room {}] Already exists (creator={}), ignoring create from user {}",
                     gameId, room.getCreatorId(), creatorId);
        }

        return room;
    }

    /**
     * Record that a player (opponent) has joined the room.
     * Does NOT handle DB state — that's MatchService's job.
     *
     * @throws IllegalStateException if room is already full
     * @throws IllegalArgumentException if room doesn't exist
     */
    public Room joinRoom(long gameId, long userId) {
        Room room = getRequiredRoom(gameId);

        int currentSize = room.connectedPlayers.size();
        
        if (currentSize >= MAX_PLAYERS) {
            GameLogger.logGameCannotStart(log, gameId, "Room is full", Map.of(
                "currentPlayers", currentSize,
                "maxPlayers", MAX_PLAYERS,
                "attemptingUserId", userId
            ));
            throw new IllegalStateException("Room " + gameId + " is full (max " + MAX_PLAYERS + " players)");
        }

        room.connectedPlayers.add(userId);
        RoomPhase oldPhase = room.phase.get();
        room.phase.compareAndSet(RoomPhase.WAITING, RoomPhase.STARTING);
        RoomPhase newPhase = room.phase.get();

        GameLogger.logPlayerJoined(log, gameId, userId, room.connectedPlayers.size(), newPhase.name());
        
        if (oldPhase != newPhase) {
            GameLogger.logGameStateTransition(log, gameId, oldPhase.name(), newPhase.name(), room.connectedPlayers.size());
        }
        
        GameLogger.logDiagnosticSnapshot(log, "After Player Join", Map.of(
            "gameId", gameId,
            "userId", userId,
            "roomSize", room.connectedPlayers.size(),
            "playerIds", room.connectedPlayers,
            "phase", newPhase.name(),
            "isFull", room.isFull()
        ));

        return room;
    }

    /**
     * Transition the room to ACTIVE and store the scheduler handle.
     * Called by MatchSchedulerService.startProgression() after candles load.
     *
     * @param gameId   The game
     * @param scheduler The ScheduledFuture for the candle tick interval
     */
    public void startGame(long gameId, ScheduledFuture<?> scheduler) {
        Room room = getRequiredRoom(gameId);

        RoomPhase oldPhase = room.phase.get();
        room.schedulerHandle = scheduler;
        room.phase.set(RoomPhase.ACTIVE);

        GameLogger.logGameStateTransition(log, gameId, oldPhase.name(), "ACTIVE", room.connectedPlayers.size());
        GameLogger.logIntervalCreated(log, gameId, 5);
        
        GameLogger.logDiagnosticSnapshot(log, "Game Started", Map.of(
            "gameId", gameId,
            "playerIds", room.connectedPlayers,
            "roomSize", room.connectedPlayers.size(),
            "phase", "ACTIVE",
            "hasScheduler", true,
            "schedulerCancelled", scheduler.isCancelled()
        ));
    }

    /**
     * End a game: cancel scheduler, set terminal phase, trigger cleanup.
     *
     * @param gameId The game
     * @param abandoned true if player disconnected, false if game finished normally
     */
    public void endGame(long gameId, boolean abandoned) {
        Room room = rooms.get(gameId);
        if (room == null) {
            log.debug("[Room {}] endGame called but room doesn't exist (already cleaned up?)", gameId);
            return;
        }

        RoomPhase oldPhase = room.phase.get();
        RoomPhase terminal = abandoned ? RoomPhase.ABANDONED : RoomPhase.FINISHED;
        room.phase.set(terminal);

        GameLogger.logGameStateTransition(log, gameId, oldPhase.name(), terminal.name(), room.connectedPlayers.size());

        // Cancel the scheduler if running
        ScheduledFuture<?> scheduler = room.schedulerHandle;
        if (scheduler != null && !scheduler.isCancelled()) {
            scheduler.cancel(false);
            room.schedulerHandle = null;
            GameLogger.logIntervalDeleted(log, gameId, abandoned ? "player_disconnect" : "game_finished");
        }

        GameLogger.logDiagnosticSnapshot(log, "Game Ended", Map.of(
            "gameId", gameId,
            "finalPhase", terminal.name(),
            "abandoned", abandoned,
            "remainingPlayers", room.connectedPlayers.size(),
            "playerIds", room.connectedPlayers
        ));

        // Auto-cleanup: disconnect all sessions, then remove room
        cleanupRoomSessions(gameId);
        removeRoom(gameId);
    }

    /**
     * Remove a room entirely. Called after endGame, or for stale rooms.
     */
    public Room removeRoom(long gameId) {
        Room room = rooms.remove(gameId);
        if (room != null) {
            // Cancel scheduler if somehow still active
            ScheduledFuture<?> s = room.schedulerHandle;
            if (s != null && !s.isCancelled()) {
                s.cancel(false);
                GameLogger.logIntervalDeleted(log, gameId, "room_cleanup");
            }
            cleanupRoomSessions(gameId);
            GameLogger.logRoomRemoved(log, gameId, "cleanup_after_end");
            
            GameLogger.logDiagnosticSnapshot(log, "Room Removed", Map.of(
                "gameId", gameId,
                "totalRoomsRemaining", rooms.size(),
                "activeSessionsRemaining", activeSessionCount()
            ));
        }
        return room;
    }

    // ===================== SESSION TRACKING =====================

    /**
     * Register a WebSocket session to a user + room.
     * Called when a player connects or sends their first trade.
     */
    public void registerSession(String sessionId, long userId, long gameId) {
        Room room = rooms.get(gameId);
        if (room != null) {
            room.sessions.put(sessionId, userId);
            room.connectedPlayers.add(userId);
        }

        sessionToGame.put(sessionId, gameId);
        sessionToUser.put(sessionId, userId);

        GameLogger.logWebSocketConnected(log, sessionId, userId, gameId);
        
        if (room != null) {
            GameLogger.logDiagnosticSnapshot(log, "Session Registered", Map.of(
                "gameId", gameId,
                "userId", userId,
                "sessionId", sessionId,
                "roomSize", room.connectedPlayers.size(),
                "phase", room.phase.get().name(),
                "totalSessions", room.sessions.size()
            ));
        }
    }

    /**
     * Unregister a WebSocket session (disconnect).
     *
     * @return A DisconnectInfo if the player was in a room, or null if untracked
     */
    public DisconnectInfo unregisterSession(String sessionId) {
        Long gameId = sessionToGame.remove(sessionId);
        Long userId = sessionToUser.remove(sessionId);

        if (gameId == null || userId == null) return null;

        Room room = rooms.get(gameId);
        if (room == null) return null;

        room.sessions.remove(sessionId);
        room.connectedPlayers.remove(userId);

        int remainingPlayers = room.connectedPlayers.size();
        
        GameLogger.logWebSocketDisconnected(log, sessionId, userId, gameId, remainingPlayers);
        GameLogger.logPlayerLeft(log, gameId, userId, remainingPlayers, "websocket_disconnect");
        
        GameLogger.logDiagnosticSnapshot(log, "Session Disconnected", Map.of(
            "gameId", gameId,
            "userId", userId,
            "sessionId", sessionId,
            "remainingPlayers", remainingPlayers,
            "remainingPlayerIds", room.connectedPlayers,
            "phase", room.phase.get().name(),
            "shouldAbandon", remainingPlayers == 0
        ));

        return new DisconnectInfo(gameId, userId, remainingPlayers);
    }

    /**
     * Info returned after a session disconnects.
     */
    public record DisconnectInfo(long gameId, long userId, int remainingPlayers) {}

    // ===================== READY-UP =====================

    /**
     * Increment the ready counter for a room.
     *
     * @return true if all players are now ready (ready count ≥ MAX_PLAYERS)
     */
    public boolean markReady(long gameId) {
        Room room = rooms.get(gameId);
        if (room == null) return false;

        int count = room.readyCount.incrementAndGet();
        log.debug("[Room {}] Ready count: {}/{}", gameId, count, MAX_PLAYERS);

        if (count >= MAX_PLAYERS) {
            room.readyCount.set(0);   // reset for next round
            return true;
        }
        return false;
    }

    // ===================== QUERIES =====================

    /** Get a room, or null if not tracked. */
    public Room getRoom(long gameId) {
        return rooms.get(gameId);
    }

    /** Get a room, throw if missing. */
    public Room getRequiredRoom(long gameId) {
        Room room = rooms.get(gameId);
        if (room == null) {
            throw new IllegalArgumentException("Room " + gameId + " does not exist in RoomManager");
        }
        return room;
    }

    /** Check if a game has an active room. */
    public boolean hasRoom(long gameId) {
        return rooms.containsKey(gameId);
    }

    /** Get the phase of a room without a DB hit. */
    public RoomPhase getPhase(long gameId) {
        Room room = rooms.get(gameId);
        return room != null ? room.getPhase() : null;
    }

    /** Get the scheduler handle for a game (used by MatchSchedulerService). */
    public ScheduledFuture<?> getScheduler(long gameId) {
        Room room = rooms.get(gameId);
        return room != null ? room.schedulerHandle : null;
    }

    /** Check if a room has a running scheduler. */
    public boolean isSchedulerRunning(long gameId) {
        Room room = rooms.get(gameId);
        return room != null && room.hasScheduler();
    }

    /** Total number of active rooms. */
    public int activeRoomCount() {
        return (int) rooms.values().stream()
                .filter(r -> r.getPhase() == RoomPhase.ACTIVE)
                .count();
    }

    /** Total rooms (all phases). */
    public int totalRoomCount() {
        return rooms.size();
    }

    /** All rooms as diagnostic snapshots. */
    public Collection<Map<String, Object>> allRoomSnapshots() {
        return rooms.values().stream().map(Room::snapshot).toList();
    }

    /** Get userId for a session (for disconnect handling). */
    public Long getUserForSession(String sessionId) {
        return sessionToUser.get(sessionId);
    }

    /** Get gameId for a session (for disconnect handling). */
    public Long getGameForSession(String sessionId) {
        return sessionToGame.get(sessionId);
    }

    /** Active session count. */
    public int activeSessionCount() {
        return sessionToUser.size();
    }

    // ===================== INTERNAL =====================

    /**
     * Remove all session mappings that belong to a game.
     */
    private void cleanupRoomSessions(long gameId) {
        sessionToGame.entrySet().removeIf(e -> e.getValue().equals(gameId));
        // Also clean user map for those sessions
        Room room = rooms.get(gameId);
        if (room != null) {
            for (String sid : room.sessions.keySet()) {
                sessionToUser.remove(sid);
            }
            room.sessions.clear();
            room.connectedPlayers.clear();
        }
    }
}
