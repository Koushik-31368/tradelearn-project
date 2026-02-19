package com.tradelearn.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Thread pool configuration for the candle-tick scheduler.
 *
 * Sizing: each active game requires ONE slot in the scheduler thread pool
 * (the 5-second tick runs, finishes quickly, and frees the thread).
 * For 10,000 concurrent games with 5s ticks and ~20ms tick duration,
 * the math is:
 *
 *     threads_needed = ceil(concurrent_games × tick_duration / tick_interval)
 *     threads_needed = ceil(10,000 × 0.02 / 5.0) = ceil(40) = 40
 *
 * We provision 64 threads for headroom (DB latency spikes, GC pauses).
 * The pool is configured as daemon threads so the JVM can shut down cleanly.
 *
 * Configurable via: {@code tradelearn.scheduler.pool-size}
 */
@Configuration
public class SchedulerConfig {

    @Value("${tradelearn.scheduler.pool-size:64}")
    private int poolSize;

    @Value("${tradelearn.scheduler.await-termination-seconds:10}")
    private int awaitTermination;

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("game-tick-");
        scheduler.setDaemon(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(awaitTermination);
        scheduler.setErrorHandler(t -> {
            // Log but don't kill the pool — individual game errors are isolated
            org.slf4j.LoggerFactory.getLogger("SchedulerConfig")
                    .error("Unhandled scheduler error", t);
        });
        return scheduler;
    }

    /**
     * Separate executor for non-game async tasks (e.g., cleanup, stats aggregation).
     */
    @Bean(name = "asyncExecutor")
    public TaskScheduler asyncExecutor() {
        ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
        executor.setPoolSize(8);
        executor.setThreadNamePrefix("async-");
        executor.setDaemon(true);
        return executor;
    }
}
