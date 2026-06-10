package com.tradelearn.server.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async executor configuration for non-blocking game operations.
 *
 * This executor is available for:
 *   - Scoreboard broadcast after trades (avoid blocking the trade response)
 *   - Future async I/O operations (analytics, metrics)
 *
 * Sizing rationale (for 10K concurrent games):
 *   Core:  16 threads — handle steady-state async tasks
 *   Max:   64 threads — absorb trade bursts
 *   Queue: 1000 — buffer during peak load
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "gameExecutor")
    public Executor gameExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("game-async-");
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
