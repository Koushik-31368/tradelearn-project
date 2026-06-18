package com.tradelearn.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    /**
     * Redis Pub/Sub listener container — only created when {@code redis.enabled=true}.
     * Without this bean, {@link com.tradelearn.server.socket.RedisWebSocketRelay}
     * is also skipped (it is @ConditionalOnProperty on the same flag).
     */
    @Bean
    @ConditionalOnProperty(name = "redis.enabled", havingValue = "true", matchIfMissing = false)
    @SuppressWarnings("null")
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}

