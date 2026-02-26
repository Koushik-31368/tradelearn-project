package com.tradelearn.server.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Redis-backed shared room state for cluster-safe multi-instance deployment.
 *
 * All shared room state lives in Redis so that every application instance
 * sees the same game rooms regardless of which instance handled the original
 * REST / WebSocket request.
 *
 * <h3>What is stored in Redis (cluster-shared):</h3>
 * <ul>
 *   <li>Room metadata  — creator, phase, creation timestamp</li>
 *   <li>Player membership — who has joined the room</li>
 *   <li>Disconnection tracking — grace period state</li>
 *   <li>Ready-up counter — pre-game sync</li>
 *   <li>Scheduler ownership — which instance runs the candle tick</li>
 *   <li>Room index — enumeration of all live game IDs</li>
 * </ul>
 *
 * <h3>What stays JVM-local (in {@link RoomManager}):</h3>
 * <ul>
 *   <li>WebSocket session maps (session ↔ user ↔ game)</li>
 *   <li>{@link java.util.concurrent.ScheduledFuture} handles</li>
 *   <li>Reconnection grace timers</li>
 * </ul>
 *
 * <h3>Key schema:</h3>
 * <pre>
 *   tl:room:{gameId}              → Hash  {creatorId, phase, createdAt}
 *   tl:room:{gameId}:players      → Set   {userId …}
 *   tl:room:{gameId}:disconnected → Set   {userId …}
 *   tl:room:{gameId}:ready        → String (atomic counter)
 *   tl:room:index                 → Set   {gameId …}
 *   tl:sched:owner:{gameId}       → String (instanceId) with TTL
 * </pre>
 *
 * <h3>Atomicity:</h3>
 * Multi-step mutations use Redis Lua scripts (server-side EVAL) so they
 * are fully atomic — no WATCH/MULTI needed, no race windows.
 *
 * <h3>Thread safety:</h3>
 * {@link StringRedisTemplate} is thread-safe (connection-pooled via Lettuce).
 * Each Lua script executes atomically on the Redis server.
 */
@Service
public class RedisRoomStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRoomStore.class);

    // ── Key Prefix ──
    private static final String P = "tl:";

    // ── TTLs ──
    private static final Duration ROOM_TTL        = Duration.ofHours(2);   // safety-net auto-expire
    private static final Duration SCHEDULER_TTL   = Duration.ofMinutes(2); // heartbeat-refreshed

    private static final int MAX_PLAYERS = 2;

    private final StringRedisTemplate redis;
    private final String instanceId;

    public RedisRoomStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
    }

    @PostConstruct
    void init() {
        log.info("[RedisRoomStore] Instance ID: {}", instanceId);
    }

    public String getInstanceId() { return instanceId; }

    // ===================== KEY BUILDERS =====================

    private String roomKey(long gid)         { return P + "room:" + gid; }
    private String playersKey(long gid)      { return P + "room:" + gid + ":players"; }
    private String disconnectedKey(long gid) { return P + "room:" + gid + ":disconnected"; }
    private String readyKey(long gid)        { return P + "room:" + gid + ":ready"; }
    private String indexKey()                { return P + "room:index"; }
    private String schedKey(long gid)        { return P + "sched:owner:" + gid; }

    // ===================== ROOM LIFECYCLE =====================

    // ── Lua: create room ONLY if it does not already exist ──
    private static final String CREATE_ROOM_LUA = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
              return 0
            end
            redis.call('HSET', KEYS[1], 'creatorId', ARGV[1], 'phase', ARGV[2], 'createdAt', ARGV[3])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            redis.call('SADD', KEYS[2], ARGV[5])
            return 1
            """;

    /**
     * Atomically create a room if it does not already exist.
     *
     * @return {@code true} if created, {@code false} if the room already existed
     */
    public boolean createRoom(long gameId, long creatorId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(CREATE_ROOM_LUA, Long.class);
        Long result = redis.execute(script,
                List.of(roomKey(gameId), indexKey()),
                String.valueOf(creatorId),
                "WAITING",
                Instant.now().toString(),
                String.valueOf(ROOM_TTL.getSeconds()),
                String.valueOf(gameId));
        return result != null && result == 1;
    }

    // ── Lua: add player, transition phase, enforce max players ──
    private static final String JOIN_ROOM_LUA = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return -1
            end
            local count = redis.call('SCARD', KEYS[2])
            if count >= tonumber(ARGV[2]) then
              return -2
            end
            redis.call('SADD', KEYS[2], ARGV[1])
            redis.call('HSET', KEYS[1], 'phase', 'STARTING')
            return redis.call('SCARD', KEYS[2])
            """;

    /**
     * Atomically join a room: add the player and transition phase to STARTING.
     *
     * @return player count after join, {@code -1} if room missing, {@code -2} if full
     */
    public int joinRoom(long gameId, long userId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(JOIN_ROOM_LUA, Long.class);
        Long result = redis.execute(script,
                List.of(roomKey(gameId), playersKey(gameId)),
                String.valueOf(userId),
                String.valueOf(MAX_PLAYERS));
        return result != null ? result.intValue() : -1;
    }

    /**
     * Check if the room hash exists in Redis.
     */
    public boolean roomExists(long gameId) {
        return Boolean.TRUE.equals(redis.hasKey(roomKey(gameId)));
    }

    // ===================== PHASE =====================

    public void setPhase(long gameId, String phase) {
        redis.opsForHash().put(roomKey(gameId), "phase", phase);
    }

    public String getPhase(long gameId) {
        Object v = redis.opsForHash().get(roomKey(gameId), "phase");
        return v != null ? v.toString() : null;
    }

    public long getCreatorId(long gameId) {
        Object v = redis.opsForHash().get(roomKey(gameId), "creatorId");
        return v != null ? Long.parseLong(v.toString()) : -1;
    }

    public String getCreatedAt(long gameId) {
        Object v = redis.opsForHash().get(roomKey(gameId), "createdAt");
        return v != null ? v.toString() : null;
    }

    // ===================== PLAYERS =====================

    public void addPlayer(long gameId, long userId) {
        redis.opsForSet().add(playersKey(gameId), String.valueOf(userId));
    }

    public void removePlayer(long gameId, long userId) {
        redis.opsForSet().remove(playersKey(gameId), String.valueOf(userId));
    }

    public Set<Long> getConnectedPlayers(long gameId) {
        Set<String> m = redis.opsForSet().members(playersKey(gameId));
        if (m == null || m.isEmpty()) return Collections.emptySet();
        return m.stream().map(Long::parseLong).collect(Collectors.toUnmodifiableSet());
    }

    public int getPlayerCount(long gameId) {
        Long sz = redis.opsForSet().size(playersKey(gameId));
        return sz != null ? sz.intValue() : 0;
    }

    public boolean isFull(long gameId) {
        return getPlayerCount(gameId) >= MAX_PLAYERS;
    }

    // ===================== DISCONNECTED (GRACE PERIOD) =====================

    public void markDisconnected(long gameId, long userId) {
        redis.opsForSet().add(disconnectedKey(gameId), String.valueOf(userId));
    }

    public void clearDisconnected(long gameId, long userId) {
        redis.opsForSet().remove(disconnectedKey(gameId), String.valueOf(userId));
    }

    public boolean isDisconnected(long gameId, long userId) {
        return Boolean.TRUE.equals(
                redis.opsForSet().isMember(disconnectedKey(gameId), String.valueOf(userId)));
    }

    public Set<Long> getDisconnectedPlayers(long gameId) {
        Set<String> m = redis.opsForSet().members(disconnectedKey(gameId));
        if (m == null || m.isEmpty()) return Collections.emptySet();
        return m.stream().map(Long::parseLong).collect(Collectors.toUnmodifiableSet());
    }

    // ===================== READY-UP =====================

    // ── Lua: increment ready counter, reset when threshold met ──
    private static final String READY_UP_LUA = """
            local c = redis.call('INCR', KEYS[1])
            if c >= tonumber(ARGV[1]) then
              redis.call('SET', KEYS[1], '0')
            end
            return c
            """;

    /**
     * Atomically increment the ready counter.
     *
     * @return counter value after increment (resets to 0 when threshold met)
     */
    public int incrementReady(long gameId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(READY_UP_LUA, Long.class);
        Long result = redis.execute(script,
                List.of(readyKey(gameId)),
                String.valueOf(MAX_PLAYERS));
        return result != null ? result.intValue() : 0;
    }

    // ===================== SCHEDULER OWNERSHIP =====================

    // ── Lua: claim or refresh scheduler ownership (SETNX-like with TTL) ──
    private static final String CLAIM_SCHED_LUA = """
            local cur = redis.call('GET', KEYS[1])
            if cur == false then
              redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
              return 1
            elseif cur == ARGV[1] then
              redis.call('EXPIRE', KEYS[1], ARGV[2])
              return 1
            else
              return 0
            end
            """;

    /**
     * Try to claim (or refresh) scheduler ownership for this instance.
     * <p>
     * Uses SET-NX semantics with TTL:
     * <ul>
     *   <li>If no owner → this instance becomes owner</li>
     *   <li>If already owner → TTL is refreshed</li>
     *   <li>If another instance owns it → returns false</li>
     * </ul>
     *
     * @return {@code true} if this instance is (now) the scheduler owner
     */
    public boolean tryClaimScheduler(long gameId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(CLAIM_SCHED_LUA, Long.class);
        Long result = redis.execute(script,
                List.of(schedKey(gameId)),
                instanceId,
                String.valueOf(SCHEDULER_TTL.getSeconds()));
        return result != null && result == 1;
    }

    /**
     * Refresh the scheduler ownership TTL. Called from every tick()
     * so the ownership key survives as long as the scheduler is alive.
     * No-op if this instance is not the current owner.
     */
    public void refreshSchedulerOwnership(long gameId) {
        String cur = redis.opsForValue().get(schedKey(gameId));
        if (instanceId.equals(cur)) {
            redis.expire(schedKey(gameId), SCHEDULER_TTL);
        }
    }

    /**
     * Release scheduler ownership. Called on stopProgression / endGame.
     * Only deletes the key if this instance is the current owner
     * (prevents one instance from releasing another's ownership).
     */
    public void releaseScheduler(long gameId) {
        String cur = redis.opsForValue().get(schedKey(gameId));
        if (instanceId.equals(cur)) {
            redis.delete(schedKey(gameId));
        }
    }

    /**
     * Check if this instance currently owns the scheduler for a game.
     */
    public boolean isSchedulerOwner(long gameId) {
        return instanceId.equals(redis.opsForValue().get(schedKey(gameId)));
    }

    // ===================== CLEANUP =====================

    /**
     * Delete ALL Redis keys for a room. Idempotent — safe to call multiple times.
     */
    public void deleteRoom(long gameId) {
        redis.delete(List.of(
                roomKey(gameId),
                playersKey(gameId),
                disconnectedKey(gameId),
                readyKey(gameId),
                schedKey(gameId)
        ));
        redis.opsForSet().remove(indexKey(), String.valueOf(gameId));
    }

    // ===================== QUERIES =====================

    /**
     * All game IDs that currently have a room in Redis.
     */
    public Set<Long> allGameIds() {
        Set<String> m = redis.opsForSet().members(indexKey());
        if (m == null || m.isEmpty()) return Collections.emptySet();
        return m.stream().map(Long::parseLong).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Count of rooms whose phase is ACTIVE. Used by Micrometer gauge.
     * O(n) scan — acceptable because Prometheus scrapes every 15 s
     * and n = number of live games (typically < 1000).
     */
    public int activeRoomCount() {
        Set<Long> ids = allGameIds();
        int count = 0;
        for (Long gid : ids) {
            if ("ACTIVE".equals(getPhase(gid))) count++;
        }
        return count;
    }

    /**
     * Total rooms across all phases.
     */
    public int totalRoomCount() {
        Long sz = redis.opsForSet().size(indexKey());
        return sz != null ? sz.intValue() : 0;
    }

    /**
     * Build a diagnostic snapshot map for a single room.
     *
     * @return snapshot map, or {@code null} if room does not exist
     */
    public Map<String, Object> snapshot(long gameId) {
        if (!roomExists(gameId)) return null;

        Map<Object, Object> hash = redis.opsForHash().entries(roomKey(gameId));
        if (hash.isEmpty()) return null;

        Map<String, Object> snap = new HashMap<>();
        snap.put("gameId", gameId);
        snap.put("creatorId", hash.getOrDefault("creatorId", ""));
        snap.put("phase", hash.getOrDefault("phase", "UNKNOWN"));
        snap.put("connectedPlayers", getConnectedPlayers(gameId));
        snap.put("playerCount", getPlayerCount(gameId));
        snap.put("createdAt", hash.getOrDefault("createdAt", ""));
        snap.put("disconnectedPlayers", getDisconnectedPlayers(gameId));

        String schedOwner = redis.opsForValue().get(schedKey(gameId));
        snap.put("schedulerOwner", schedOwner != null ? schedOwner : "none");
        snap.put("schedulerOwnedByThisInstance", instanceId.equals(schedOwner));

        return snap;
    }
}
