package com.tradelearn.server.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    /**
     * Use the same REDIS_HOST / REDIS_PORT variables that Spring Data Redis reads,
     * so both clients connect to the same server.  A 3-second connect timeout
     * prevents the app from hanging at startup when Redis is slow to become ready.
     */
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        String redisUrl = "redis://" + redisHost + ":" + redisPort;

        config.useSingleServer()
              .setAddress(redisUrl)
              .setConnectTimeout(3000)   // 3 s — fail fast, don't block startup
              .setTimeout(3000);

        return Redisson.create(config);
    }
}
