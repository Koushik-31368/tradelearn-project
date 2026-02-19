package com.tradelearn.server.socket;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

/**
 * Redis-backed WebSocket message relay for horizontal scaling.
 *
 * Problem: When running N server instances behind a load balancer,
 * a WebSocket client connected to Instance A won't receive messages
 * published by Instance B's {@link SimpMessagingTemplate}.
 *
 * Solution: Every outbound WebSocket broadcast is also published
 * to a Redis Pub/Sub channel. All instances subscribe to that
 * channel and re-broadcast locally, ensuring every connected
 * client receives the message regardless of which instance
 * originally published it.
 *
 * Channel naming convention:
 *   tradelearn:ws:game:{gameId}:{eventType}
 *
 * This replaces no existing code — existing services continue to
 * call SimpMessagingTemplate directly for single-instance deployments.
 * For multi-instance, inject this relay instead.
 *
 * Scale: Redis Pub/Sub is O(N) where N = subscribers.
 *        At 10K games × 5 events/sec = 50K msgs/sec — well within
 *        Redis's 500K+ msg/sec throughput on a single node.
 */
@Component
public class RedisWebSocketRelay implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisWebSocketRelay.class);
    private static final String CHANNEL_PREFIX = "tradelearn:ws:";
    private static final String INSTANCE_ID = java.util.UUID.randomUUID().toString().substring(0, 8);

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisWebSocketRelay(StringRedisTemplate redisTemplate,
                               RedisMessageListenerContainer listenerContainer,
                               SimpMessagingTemplate messagingTemplate,
                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        // Subscribe to the wildcard game events channel
        listenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL_PREFIX + "broadcast"));
        log.info("[Redis Relay] Instance {} subscribed to {}", INSTANCE_ID, CHANNEL_PREFIX + "broadcast");
    }

    // ==================== PUBLISH ====================

    /**
     * Publish a game event to Redis so all instances can relay it.
     *
     * @param destination  The STOMP destination (e.g. "/topic/game/101/candle")
     * @param payload      The message payload (will be JSON-serialized)
     */
    public void broadcast(String destination, Object payload) {
        try {
            RelayMessage msg = new RelayMessage(INSTANCE_ID, destination, payload);
            String json = objectMapper.writeValueAsString(msg);
            redisTemplate.convertAndSend(CHANNEL_PREFIX + "broadcast", json);
        } catch (JsonProcessingException e) {
            log.error("[Redis Relay] Failed to serialize broadcast: {}", e.getMessage());
            // Fall back to local-only delivery
            messagingTemplate.convertAndSend(destination, payload);
        }
    }

    // ==================== RECEIVE ====================

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            RelayMessage msg = objectMapper.readValue(json, RelayMessage.class);

            // De-duplicate: don't re-broadcast messages from this instance
            if (INSTANCE_ID.equals(msg.sourceInstance)) {
                return;
            }

            messagingTemplate.convertAndSend(msg.destination, msg.payload);

        } catch (Exception e) {
            log.error("[Redis Relay] Failed to process incoming message: {}", e.getMessage());
        }
    }

    // ==================== INTERNAL DTO ====================

    /**
     * Envelope for Redis Pub/Sub messages.
     */
    public static class RelayMessage {
        public String sourceInstance;
        public String destination;
        public Object payload;

        public RelayMessage() {}

        public RelayMessage(String sourceInstance, String destination, Object payload) {
            this.sourceInstance = sourceInstance;
            this.destination = destination;
            this.payload = payload;
        }
    }

    // ==================== DISTRIBUTED PRESENCE ====================

    /**
     * Register a game as "owned" by this instance in Redis.
     * Other instances can check which instance owns a game for
     * targeted communication (e.g., admin commands).
     *
     * TTL = 60s, refreshed on each tick. If an instance crashes,
     * the key expires and another instance can adopt the game.
     */
    public void registerGameOwnership(long gameId) {
        String key = "tradelearn:game:owner:" + gameId;
        redisTemplate.opsForValue().set(key, INSTANCE_ID,
                java.time.Duration.ofSeconds(60));
    }

    /**
     * Check which server instance owns a game.
     */
    public String getGameOwner(long gameId) {
        return redisTemplate.opsForValue().get("tradelearn:game:owner:" + gameId);
    }

    /**
     * Remove game ownership on cleanup.
     */
    public void releaseGameOwnership(long gameId) {
        redisTemplate.delete("tradelearn:game:owner:" + gameId);
    }

    /**
     * Publish a global metric to Redis for centralized monitoring.
     */
    public void publishMetric(String metricName, Map<String, Object> data) {
        try {
            data.put("instance", INSTANCE_ID);
            data.put("timestamp", System.currentTimeMillis());
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.convertAndSend("tradelearn:metrics:" + metricName, json);
        } catch (JsonProcessingException e) {
            log.warn("[Redis Relay] Failed to publish metric {}: {}", metricName, e.getMessage());
        }
    }

    public String getInstanceId() {
        return INSTANCE_ID;
    }
}
