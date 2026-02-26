package com.tradelearn.server.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        String redisUrl = System.getenv("REDIS_URL");

        if (redisUrl == null || redisUrl.isEmpty()) {
            redisUrl = "redis://localhost:6379";
        }

        config.useSingleServer()
              .setAddress(redisUrl);

        return Redisson.create(config);
    }
}
