package com.tradelearn.server.market.replay;

import com.tradelearn.server.market.model.StockSymbol;
import com.tradelearn.server.market.repository.StockCandleDailyRepository;
import com.tradelearn.server.market.repository.StockSymbolRepository;
import com.tradelearn.server.market.service.CandleService;
import com.tradelearn.server.websocket.GameBroadcaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulated-live tick engine for solo simulator sessions.
 *
 * <p>This engine is <b>separate</b> from {@code MatchSchedulerService} (which drives
 * per-game candle progression for multiplayer matches). This engine serves the
 * solo simulator: each user session gets its own per-symbol candle replay stream
 * pushed over WebSocket.
 *
 * <h3>WebSocket topic</h3>
 * {@code /topic/simulator/{sessionId}/tick}
 *
 * <h3>Playback speed</h3>
 * {@code tickIntervalMs} controls how fast real-time maps to market time.
 * At the default 5000 ms/candle with daily candles:
 * <ul>
 *   <li>1 trading week  (5 candles)  = 25 real seconds</li>
 *   <li>1 trading month (~21 candles)= ~1.75 real minutes</li>
 *   <li>1 trading year (~252 candles)= ~21 real minutes</li>
 * </ul>
 * Clients can request faster playback by reducing {@code tickIntervalMs} (min: 500 ms).
 *
 * <h3>Epoch fairness</h3>
 * The solo simulator does not use the {@code EpochLockstepEngine} — it is single-user,
 * so informational-asymmetry fairness between players is not relevant.
 * Trades placed via the simulator REST API are settled against the current
 * simulator session's last-broadcast tick price (held in {@code lastTickPrice}).
 */
@Component
public class TickReplayEngine {

    private static final Logger log = LoggerFactory.getLogger(TickReplayEngine.class);

    /** Minimum allowed tick interval (guards against runaway CPU). */
    private static final long MIN_TICK_MS = 500L;
    /** Maximum allowed tick interval. */
    private static final long MAX_TICK_MS = 60_000L;
    /** Default: 5 s per candle, matching the multiplayer tick rate. */
    @SuppressWarnings("unused") // documented default; callers may reference this in future
    private static final long DEFAULT_TICK_MS = 5_000L;

    /** In-memory registry of active simulator sessions. */
    public record SimulatorSession(
            String sessionId,
            String ticker,           // yfinance-form ticker
            List<CandleService.Candle> candles,
            AtomicInteger currentIndex,
            long tickIntervalMs,
            ScheduledFuture<?> future,
            double lastTickPrice     // mutable via lastTickPrices map below
    ) {}

    private final TaskScheduler taskScheduler;
    private final StockSymbolRepository symbolRepo;
    private final StockCandleDailyRepository candleRepo;
    private final GameBroadcaster broadcaster;

    /** Active sessions: sessionId → session metadata */
    private final Map<String, SimulatorSession> activeSessions = new ConcurrentHashMap<>();

    /** Last broadcast price per session (for trade settlement). */
    private final Map<String, Double> lastTickPrices = new ConcurrentHashMap<>();

    public TickReplayEngine(TaskScheduler taskScheduler,
                            StockSymbolRepository symbolRepo,
                            StockCandleDailyRepository candleRepo,
                            GameBroadcaster broadcaster) {
        this.taskScheduler = taskScheduler;
        this.symbolRepo = symbolRepo;
        this.candleRepo = candleRepo;
        this.broadcaster = broadcaster;
    }

    // ── Session Lifecycle ──────────────────────────────────────────────────

    /**
     * Starts a new simulator replay session.
     *
     * @param userId        The user this session belongs to (used in sessionId).
     * @param bareTicker    Bare ticker, e.g. {@code "RELIANCE"}.
     * @param candleCount   Number of candles to replay (e.g. 60 for ~3 months).
     * @param tickIntervalMs Real-world milliseconds between candle advances.
     * @return The assigned session ID.
     * @throws IllegalArgumentException if the symbol is unknown or has no candle data.
     */
    public String startSession(long userId, String bareTicker, int candleCount, long tickIntervalMs) {
        long clampedInterval = Math.max(MIN_TICK_MS, Math.min(MAX_TICK_MS, tickIntervalMs));

        // Resolve symbol
        StockSymbol symbol = symbolRepo.findByBareTicker(bareTicker.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown symbol: " + bareTicker + ". Check the NSE watchlist."));

        if (symbol.getDataMode() != StockSymbol.DataMode.REPLAY) {
            throw new IllegalArgumentException(
                    "Symbol " + bareTicker + " is LIVE_US — use Finnhub mode for US stocks.");
        }

        // Load candle slice (random window)
        List<CandleService.Candle> candles = loadRandomSlice(symbol.getTicker(), candleCount);
        if (candles.isEmpty()) {
            throw new IllegalStateException(
                    "No candle data available for " + bareTicker + ". Run the ingestion script first.");
        }

        String sessionId = "sim-" + userId + "-" + System.currentTimeMillis();
        AtomicInteger idx = new AtomicInteger(0);

        // Broadcast first candle immediately
        CandleService.Candle first = candles.get(0);
        broadcastTick(sessionId, first, 0, candles.size());
        lastTickPrices.put(sessionId, first.getClose());

        // Schedule subsequent ticks
        @SuppressWarnings("null") // Duration.ofMillis is non-null; @NonNull required by TaskScheduler signature
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                () -> tick(sessionId, candles, idx, clampedInterval),
                Duration.ofMillis(clampedInterval)
        );

        activeSessions.put(sessionId, new SimulatorSession(
                sessionId, symbol.getTicker(), candles,
                idx, clampedInterval, future, first.getClose()
        ));

        log.info("[TickReplay] Started session {} for user {} — ticker={}, candles={}, interval={}ms",
                sessionId, userId, symbol.getTicker(), candles.size(), clampedInterval);
        return sessionId;
    }

    /**
     * Stops a simulator session and cancels its scheduled task.
     *
     * @param sessionId The session to stop.
     */
    public void stopSession(String sessionId) {
        SimulatorSession session = activeSessions.remove(sessionId);
        if (session != null) {
            session.future().cancel(false);
            lastTickPrices.remove(sessionId);
            log.info("[TickReplay] Stopped session {}", sessionId);
        }
    }

    /**
     * Returns the last broadcast close price for a session.
     * Used by the simulator REST API to settle trades at the current market price.
     *
     * @throws IllegalArgumentException if the session does not exist.
     */
    public double getCurrentPrice(String sessionId) {
        Double price = lastTickPrices.get(sessionId);
        if (price == null) {
            throw new IllegalArgumentException("Simulator session not found: " + sessionId);
        }
        return price;
    }

    /** Returns true if the session is active. */
    public boolean sessionExists(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }

    /** Returns all active session IDs (for admin/metrics). */
    public Set<String> activeSessions() {
        return Collections.unmodifiableSet(activeSessions.keySet());
    }

    // ── Internal tick logic ────────────────────────────────────────────────

    private void tick(String sessionId, List<CandleService.Candle> candles,
                      AtomicInteger idx, long intervalMs) {
        int nextIdx = idx.incrementAndGet();

        if (nextIdx >= candles.size()) {
            // Replay complete — stop and notify client
            log.info("[TickReplay] Session {} exhausted candles — stopping", sessionId);
            broadcaster.sendLocal("/topic/simulator/" + sessionId + "/tick",
                    Map.of("event", "REPLAY_COMPLETE", "sessionId", sessionId));
            stopSession(sessionId);
            return;
        }

        CandleService.Candle candle = candles.get(nextIdx);
        lastTickPrices.put(sessionId, candle.getClose());
        broadcastTick(sessionId, candle, nextIdx, candles.size());
    }

    private void broadcastTick(String sessionId, CandleService.Candle candle,
                               int index, int total) {
        Map<String, Object> payload = Map.of(
                "event",     "CANDLE",
                "sessionId", sessionId,
                "candle",    candle,
                "index",     index,
                "remaining", total - index - 1,
                "price",     candle.getClose()
        );
        broadcaster.sendLocal("/topic/simulator/" + sessionId + "/tick", payload);
    }

    private List<CandleService.Candle> loadRandomSlice(String yfinanceTicker, int requestedCount) {
        LocalDate minDate = candleRepo.findMinDateForTicker(yfinanceTicker);
        LocalDate maxDate = candleRepo.findMaxDateForTicker(yfinanceTicker);

        if (minDate == null || maxDate == null) return List.of();

        // Pick a random start within the available window
        int calendarBuffer = requestedCount + (requestedCount / 2) + 14;
        long totalDays = minDate.until(maxDate, java.time.temporal.ChronoUnit.DAYS);

        if (totalDays < calendarBuffer) {
            // Not enough room to randomize — use the start
            return candleRepo.findByTickerAndDateRange(yfinanceTicker, minDate, maxDate)
                    .stream()
                    .limit(requestedCount)
                    .map(row -> {
                        CandleService.Candle c = new CandleService.Candle();
                        c.setDate(row.getTradeDate().toString());
                        c.setOpen(row.getOpenPrice().doubleValue());
                        c.setHigh(row.getHighPrice().doubleValue());
                        c.setLow(row.getLowPrice().doubleValue());
                        c.setClose(row.getClosePrice().doubleValue());
                        c.setVolume(row.getVolume());
                        return c;
                    })
                    .toList();
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            long offset = ThreadLocalRandom.current().nextLong(totalDays - calendarBuffer);
            LocalDate sliceStart = minDate.plusDays(offset);
            LocalDate sliceEnd = sliceStart.plusDays(calendarBuffer);

            List<CandleService.Candle> candles = candleRepo
                    .findByTickerAndDateRange(yfinanceTicker, sliceStart, sliceEnd)
                    .stream()
                    .limit(requestedCount)
                    .map(row -> {
                        CandleService.Candle c = new CandleService.Candle();
                        c.setDate(row.getTradeDate().toString());
                        c.setOpen(row.getOpenPrice().doubleValue());
                        c.setHigh(row.getHighPrice().doubleValue());
                        c.setLow(row.getLowPrice().doubleValue());
                        c.setClose(row.getClosePrice().doubleValue());
                        c.setVolume(row.getVolume());
                        return c;
                    })
                    .toList();

            if (candles.size() >= Math.min(requestedCount, 5)) {
                return candles;
            }
        }
        return List.of();
    }

    @PreDestroy
    public void shutdown() {
        log.info("[TickReplay] Shutting down — stopping {} active sessions", activeSessions.size());
        new HashSet<>(activeSessions.keySet()).forEach(this::stopSession);
    }
}
