package com.tradelearn.server.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.MDC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Structured logging utility for multiplayer game debugging.
 * 
 * Provides consistent, timestamp-prefixed, JSON-structured logging
 * for all critical game lifecycle events:
 *   - Room creation/join/leave
 *   - Game state transitions
 *   - Interval creation/deletion
 *   - Player connections/disconnections
 *   - Error conditions
 * 
 * All logs include:
 *   - ISO-8601 timestamp with millisecond precision
 *   - Game ID (when applicable)
 *   - User IDs
 *   - Room size
 *   - Game state
 *   - Additional contextual data
 * 
 * Usage:
 *   GameLogger.logRoomCreated(log, gameId, creatorId);
 *   GameLogger.logGameStateTransition(log, gameId, oldState, newState, playerCount);
 *   GameLogger.logIntervalCreated(log, gameId);
 */
public class GameLogger {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter formatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneId.of("UTC"));

    // ==================== STRUCTURED LOG BUILDERS ====================

    /**
     * Base structured log entry with timestamp
     */
    private static class LogEntry {
        public String timestamp;
        public String event;
        public Map<String, Object> data = new HashMap<>();

        public LogEntry(String event) {
            this.timestamp = formatter.format(Instant.now());
            this.event = event;
        }

        public LogEntry add(String key, Object value) {
            if (value != null) {
                data.put(key, value);
            }
            return this;
        }

        public String toJson() {
            try {
                return objectMapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return String.format("{\"error\":\"Failed to serialize log\",\"event\":\"%s\"}", event);
            }
        }

        public String toReadable() {
            StringBuilder sb = new StringBuilder();
            sb.append("⏱ ").append(timestamp).append(" | ");
            sb.append("📋 ").append(event).append(" | ");
            
            data.forEach((key, value) -> {
                sb.append(key).append("=").append(value).append(" ");
            });
            
            return sb.toString().trim();
        }
    }

    // ==================== ROOM LIFECYCLE ====================

    /**
     * Log room creation
     */
    public static void logRoomCreated(Logger log, long gameId, long creatorId) {
        LogEntry entry = new LogEntry("ROOM_CREATED")
                .add("gameId", gameId)
                .add("creatorId", creatorId)
                .add("roomSize", 1)
                .add("phase", "WAITING");
        
        log.info("🏠 {}", entry.toReadable());
    }

    /**
     * Log player joining room
     */
    public static void logPlayerJoined(Logger log, long gameId, long userId, int roomSize, String phase) {
        LogEntry entry = new LogEntry("PLAYER_JOINED")
                .add("gameId", gameId)
                .add("userId", userId)
                .add("roomSize", roomSize)
                .add("phase", phase)
                .add("isFull", roomSize >= 2);
        
        log.info("👤 {}", entry.toReadable());
    }

    /**
     * Log player leaving room
     */
    public static void logPlayerLeft(Logger log, long gameId, long userId, int remainingPlayers, String reason) {
        LogEntry entry = new LogEntry("PLAYER_LEFT")
                .add("gameId", gameId)
                .add("userId", userId)
                .add("remainingPlayers", remainingPlayers)
                .add("reason", reason);
        
        log.warn("🚪 {}", entry.toReadable());
    }

    /**
     * Log room removal
     */
    public static void logRoomRemoved(Logger log, long gameId, String reason) {
        LogEntry entry = new LogEntry("ROOM_REMOVED")
                .add("gameId", gameId)
                .add("reason", reason);
        
        log.info("🗑️ {}", entry.toReadable());
    }

    // ==================== GAME STATE TRANSITIONS ====================

    /**
     * Log game state transition
     */
    public static void logGameStateTransition(Logger log, long gameId, String oldState, 
                                               String newState, int playerCount) {
        LogEntry entry = new LogEntry("GAME_STATE_TRANSITION")
                .add("gameId", gameId)
                .add("oldState", oldState)
                .add("newState", newState)
                .add("playerCount", playerCount);
        
        log.info("🔄 {}", entry.toReadable());
    }

    /**
     * Log game start attempt
     */
    public static void logGameStartAttempt(Logger log, long gameId, long creatorId, 
                                           Long opponentId, String currentStatus) {
        LogEntry entry = new LogEntry("GAME_START_ATTEMPT")
                .add("gameId", gameId)
                .add("creatorId", creatorId)
                .add("opponentId", opponentId)
                .add("currentStatus", currentStatus)
                .add("hasOpponent", opponentId != null)
                .add("canStart", opponentId != null && "ACTIVE".equals(currentStatus));
        
        log.info("🎮 {}", entry.toReadable());
    }

    /**
     * Log game start success
     */
    public static void logGameStarted(Logger log, long gameId, long creatorId, long opponentId) {
        LogEntry entry = new LogEntry("GAME_STARTED")
                .add("gameId", gameId)
                .add("creatorId", creatorId)
                .add("opponentId", opponentId)
                .add("playerCount", 2);
        
        log.info("✅ {}", entry.toReadable());
    }

    /**
     * Log why a game cannot start
     */
    public static void logGameCannotStart(Logger log, long gameId, String reason, 
                                          Map<String, Object> context) {
        LogEntry entry = new LogEntry("GAME_CANNOT_START")
                .add("gameId", gameId)
                .add("reason", reason);
        
        if (context != null) {
            context.forEach(entry::add);
        }
        
        log.error("❌ {}", entry.toReadable());
    }

    /**
     * Log game finished
     */
    public static void logGameFinished(Logger log, long gameId, Long winnerId, 
                                       double creatorBalance, double opponentBalance) {
        LogEntry entry = new LogEntry("GAME_FINISHED")
                .add("gameId", gameId)
                .add("winnerId", winnerId)
                .add("creatorBalance", creatorBalance)
                .add("opponentBalance", opponentBalance);
        
        log.info("🏁 {}", entry.toReadable());
    }

    // ==================== INTERVAL/SCHEDULER ====================

    /**
     * Log interval/scheduler creation
     */
    public static void logIntervalCreated(Logger log, long gameId, int intervalSeconds) {
        LogEntry entry = new LogEntry("INTERVAL_CREATED")
                .add("gameId", gameId)
                .add("intervalSeconds", intervalSeconds)
                .add("task", "candle_progression");
        
        log.info("⏰ {}", entry.toReadable());
    }

    /**
     * Log interval tick
     */
    public static void logIntervalTick(Logger log, long gameId, int currentCandle, 
                                       int totalCandles, double price) {
        LogEntry entry = new LogEntry("INTERVAL_TICK")
                .add("gameId", gameId)
                .add("currentCandle", currentCandle)
                .add("totalCandles", totalCandles)
                .add("remaining", totalCandles - currentCandle - 1)
                .add("price", price);
        
        log.debug("⏳ {}", entry.toReadable());
    }

    /**
     * Log interval deletion/cancellation
     */
    public static void logIntervalDeleted(Logger log, long gameId, String reason) {
        LogEntry entry = new LogEntry("INTERVAL_DELETED")
                .add("gameId", gameId)
                .add("reason", reason);
        
        log.info("⏹️ {}", entry.toReadable());
    }

    // ==================== WEBSOCKET CONNECTIONS ====================

    /**
     * Log WebSocket connection
     */
    public static void logWebSocketConnected(Logger log, String sessionId, long userId, long gameId) {
        LogEntry entry = new LogEntry("WEBSOCKET_CONNECTED")
                .add("sessionId", sessionId)
                .add("userId", userId)
                .add("gameId", gameId);
        
        log.info("🔌 {}", entry.toReadable());
    }

    /**
     * Log WebSocket disconnection
     */
    public static void logWebSocketDisconnected(Logger log, String sessionId, long userId, 
                                                long gameId, int remainingPlayers) {
        LogEntry entry = new LogEntry("WEBSOCKET_DISCONNECTED")
                .add("sessionId", sessionId)
                .add("userId", userId)
                .add("gameId", gameId)
                .add("remainingPlayers", remainingPlayers);
        
        log.warn("🔌❌ {}", entry.toReadable());
    }

    // ==================== TRADES ====================

    /**
     * Log trade placement
     */
    public static void logTradePlaced(Logger log, long gameId, long userId, String type, 
                                      int quantity, String symbol, double price) {
        LogEntry entry = new LogEntry("TRADE_PLACED")
                .add("gameId", gameId)
                .add("userId", userId)
                .add("type", type)
                .add("quantity", quantity)
                .add("symbol", symbol)
                .add("price", price);
        
        log.info("💰 {}", entry.toReadable());
    }

    /**
     * Log trade rejection
     */
    public static void logTradeRejected(Logger log, long gameId, long userId, String type, 
                                        int quantity, String reason) {
        LogEntry entry = new LogEntry("TRADE_REJECTED")
                .add("gameId", gameId)
                .add("userId", userId)
                .add("type", type)
                .add("quantity", quantity)
                .add("reason", reason);
        
        log.warn("💰❌ {}", entry.toReadable());
    }

    // ==================== ERRORS ====================

    /**
     * Log error with full context
     */
    public static void logError(Logger log, String operation, long gameId, 
                                Exception e, Map<String, Object> context) {
        LogEntry entry = new LogEntry("ERROR")
                .add("operation", operation)
                .add("gameId", gameId)
                .add("error", e.getClass().getSimpleName())
                .add("message", e.getMessage());
        
        if (context != null) {
            context.forEach(entry::add);
        }
        
        log.error("❌❌❌ {}", entry.toReadable(), e);
    }

    /**
     * Log error without gameId
     */
    public static void logError(Logger log, String operation, Exception e, Map<String, Object> context) {
        LogEntry entry = new LogEntry("ERROR")
                .add("operation", operation)
                .add("error", e.getClass().getSimpleName())
                .add("message", e.getMessage());
        
        if (context != null) {
            context.forEach(entry::add);
        }
        
        log.error("❌❌❌ {}", entry.toReadable(), e);
    }

    // ==================== DIAGNOSTIC ====================

    /**
     * Log diagnostic snapshot
     */
    public static void logDiagnosticSnapshot(Logger log, String label, Map<String, Object> data) {
        LogEntry entry = new LogEntry("DIAGNOSTIC_SNAPSHOT")
                .add("label", label);
        
        if (data != null) {
            data.forEach(entry::add);
        }
        
        log.info("📊 {}", entry.toReadable());
    }

    // ==================== MDC CONTEXT ====================

    /**
     * Set gameId in MDC for request tracking
     */
    public static void setGameContext(long gameId) {
        MDC.put("gameId", String.valueOf(gameId));
    }

    /**
     * Set userId in MDC for request tracking
     */
    public static void setUserContext(long userId) {
        MDC.put("userId", String.valueOf(userId));
    }

    /**
     * Clear MDC context
     */
    public static void clearContext() {
        MDC.clear();
    }
}
