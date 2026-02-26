package com.tradelearn.server.service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Centralised Micrometer metrics for the TradeLearn game engine.
 *
 * All metric updates use pre-registered counters/timers so the
 * hot path (trade execution, candle tick) does zero lookup or
 * allocation — just a single atomic CAS increment.
 *
 * Gauges are backed by live suppliers (lambdas that poll RoomManager
 * / MatchmakingService) so they never need explicit updates.
 *
 * Thread-safe: Micrometer counters & timers use lock-free CAS.
 * No blocking calls. No performance regression.
 *
 * Exposed via: /actuator/prometheus
 */
@Service
public class GameMetricsService {

    // ===================== COUNTERS =====================

    private final Counter tradesTotal;
    private final Counter tradesRejectedRateLimit;
    private final Counter matchesCreated;
    private final Counter matchesCompleted;
    private final Counter matchesForfeited;
    private final Counter reconnectSuccess;
    private final Counter reconnectTimeout;

    // ===================== TIMERS =====================

    private final Timer tradeExecutionTimer;
    private final Timer candleTickTimer;

    // ===================== CONSTRUCTION =====================

    public GameMetricsService(MeterRegistry registry,
                              RoomManager roomManager,
                              MatchmakingService matchmakingService) {

        // ── Counters ──

        this.tradesTotal = Counter.builder("trades.total")
                .description("Total trades executed (BUY, SELL, SHORT, COVER)")
                .register(registry);

        this.tradesRejectedRateLimit = Counter.builder("trades.rejected.rate_limit")
                .description("Trades rejected due to per-player rate limiting")
                .register(registry);

        this.matchesCreated = Counter.builder("matches.created")
                .description("Total matches created (custom + auto-ranked)")
                .register(registry);

        this.matchesCompleted = Counter.builder("matches.completed")
                .description("Matches that finished normally (all candles consumed)")
                .register(registry);

        this.matchesForfeited = Counter.builder("matches.forfeited")
                .description("Matches abandoned due to player disconnect timeout")
                .register(registry);

        this.reconnectSuccess = Counter.builder("reconnect.success")
                .description("Successful WebSocket reconnections within grace period")
                .register(registry);

        this.reconnectTimeout = Counter.builder("reconnect.timeout")
                .description("Reconnection grace periods that expired (led to forfeit)")
                .register(registry);

        // ── Timers (nanosecond precision, published as seconds) ──

        this.tradeExecutionTimer = Timer.builder("trade.execution.time")
                .description("Time to validate + persist + broadcast a trade")
                .publishPercentileHistogram()
                .register(registry);

        this.candleTickTimer = Timer.builder("candle.tick.time")
                .description("Time to advance candle + broadcast + scoreboard")
                .publishPercentileHistogram()
                .register(registry);

        // ── Gauges (polled lazily — no explicit update needed) ──

        registry.gauge("active.games", roomManager, RoomManager::activeRoomCount);

        registry.gauge("matchmaking.queue.size", matchmakingService, MatchmakingService::queueSize);
    }

    // ===================== RECORDING API =====================

    // ── Counters (single atomic increment, ~2 ns) ──

    public void recordTrade()                  { tradesTotal.increment(); }
    public void recordTradeRejectedRateLimit() { tradesRejectedRateLimit.increment(); }
    public void recordMatchCreated()           { matchesCreated.increment(); }
    public void recordMatchCompleted()         { matchesCompleted.increment(); }
    public void recordMatchForfeited()         { matchesForfeited.increment(); }
    public void recordReconnectSuccess()       { reconnectSuccess.increment(); }
    public void recordReconnectTimeout()       { reconnectTimeout.increment(); }

    // ── Timers ──

    /**
     * Record trade execution duration (nanoseconds).
     */
    public void recordTradeTime(long nanos) {
        tradeExecutionTimer.record(nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Record candle tick duration (nanoseconds).
     */
    public void recordCandleTickTime(long nanos) {
        candleTickTimer.record(nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Convenience: wrap a {@link Supplier} in a timer and return its result.
     */
    public <T> T timeTradeExecution(Supplier<T> action) {
        return tradeExecutionTimer.record(action);
    }

    /**
     * Convenience: wrap a {@link Runnable} in the candle-tick timer.
     */
    public void timeCandleTick(Runnable action) {
        candleTickTimer.record(action);
    }
}
