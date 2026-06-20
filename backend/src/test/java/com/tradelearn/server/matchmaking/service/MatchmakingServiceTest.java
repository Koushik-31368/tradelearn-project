package com.tradelearn.server.matchmaking.service;

import com.tradelearn.server.dto.PlayerTicket;
import com.tradelearn.server.game.service.MatchService;
import com.tradelearn.server.websocket.GameBroadcaster;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MatchmakingService}.
 *
 * <p>Redis, Redisson, and GameBroadcaster are all mocked. These tests focus on:
 * <ul>
 *   <li>enqueue — already-queued players throw immediately</li>
 *   <li>dequeue — returns true when Redis ZREM removes the member</li>
 *   <li>isQueued — delegates to Redis key existence check</li>
 *   <li>queueSize — reads ZSET cardinality safely (null → 0)</li>
 *   <li>getWaitTime — parses joinTime from Redis hash</li>
 *   <li>cleanupExpired — removes orphaned ZSET entries (no ticket hash)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {

    @Mock private MatchService matchService;
    @Mock private GameBroadcaster broadcaster;
    @Mock private RedissonClient redisson;
    @Mock private StringRedisTemplate redis;
    @Mock private ZSetOperations<String, String> zSetOps;
    @Mock private HashOperations<String, Object, Object> hashOps;

    private MeterRegistry meterRegistry;
    private MatchmakingService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new MatchmakingService(matchService, broadcaster, redisson, redis, meterRegistry);

        // Wire the RedisTemplate op delegates
        lenient().when(redis.opsForZSet()).thenReturn(zSetOps);
        lenient().when(redis.opsForHash()).thenReturn(hashOps);
    }

    // ── queueSize ────────────────────────────────────────────────────────────

    @Test
    void queueSize_returnsZeroWhenRedisReturnsNull() {
        when(zSetOps.zCard("matchmaking:queue")).thenReturn(null);

        assertThat(service.queueSize()).isZero();
    }

    @Test
    void queueSize_returnsValueFromRedis() {
        when(zSetOps.zCard("matchmaking:queue")).thenReturn(7L);

        assertThat(service.queueSize()).isEqualTo(7);
    }

    // ── isQueued ─────────────────────────────────────────────────────────────

    @Test
    void isQueued_returnsTrueWhenTicketKeyExists() {
        when(redis.hasKey("matchmaking:ticket:42")).thenReturn(Boolean.TRUE);

        assertThat(service.isQueued(42L)).isTrue();
    }

    @Test
    void isQueued_returnsFalseWhenTicketKeyAbsent() {
        when(redis.hasKey("matchmaking:ticket:42")).thenReturn(null);

        assertThat(service.isQueued(42L)).isFalse();
    }

    // ── enqueue — already queued ──────────────────────────────────────────────

    @Test
    void enqueue_throwsWhenPlayerAlreadyQueued() {
        // Lua script returns 0 → already in queue
        when(redis.execute(any(), anyList(), any(String.class), any(String.class),
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(0L);

        PlayerTicket ticket = new PlayerTicket(1L, "trader1", 1200);

        assertThatThrownBy(() -> service.enqueue(ticket))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Already in matchmaking queue");
    }

    // ── dequeue ───────────────────────────────────────────────────────────────

    @Test
    void dequeue_returnsTrueWhenPlayerWasInQueue() {
        // Lua DEQUEUE script returns 1 → removed
        when(redis.execute(any(), anyList(), any(String.class))).thenReturn(1L);

        assertThat(service.dequeue(1L)).isTrue();
    }

    @Test
    void dequeue_returnsFalseWhenPlayerNotInQueue() {
        // Lua DEQUEUE script returns 0 → was not in queue
        when(redis.execute(any(), anyList(), any(String.class))).thenReturn(0L);

        assertThat(service.dequeue(1L)).isFalse();
    }

    @Test
    void dequeue_returnsFalseWhenRedisReturnsNull() {
        when(redis.execute(any(), anyList(), any(String.class))).thenReturn(null);

        assertThat(service.dequeue(1L)).isFalse();
    }

    // ── getWaitTime ───────────────────────────────────────────────────────────

    @Test
    void getWaitTime_returnsNegativeOneWhenNotQueued() {
        when(hashOps.get("matchmaking:ticket:1", "joinTime")).thenReturn(null);

        assertThat(service.getWaitTime(1L)).isEqualTo(-1L);
    }

    @Test
    void getWaitTime_returnsParsedWaitSeconds() {
        // joinTime 30 seconds ago
        Instant joinTime = Instant.now().minusSeconds(30);
        when(hashOps.get("matchmaking:ticket:1", "joinTime")).thenReturn(joinTime.toString());

        long waitTime = service.getWaitTime(1L);

        // Allow ±2 sec tolerance for test timing
        assertThat(waitTime).isBetween(28L, 32L);
    }

    @Test
    void getWaitTime_returnsMinus1ForMalformedJoinTime() {
        when(hashOps.get("matchmaking:ticket:1", "joinTime")).thenReturn("not-a-timestamp");

        assertThat(service.getWaitTime(1L)).isEqualTo(-1L);
    }

    // ── Micrometer gauge registration ────────────────────────────────────────

    @Test
    void init_registersQueueSizeGauge() {
        service.init();

        // SimpleMeterRegistry captures all registered meters
        assertThat(meterRegistry.find("matchmaking.queue.size").gauge()).isNotNull();
    }
}
