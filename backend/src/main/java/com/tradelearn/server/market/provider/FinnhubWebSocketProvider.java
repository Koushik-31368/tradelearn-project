package com.tradelearn.server.market.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradelearn.server.market.model.StockSymbol;
import com.tradelearn.server.market.repository.StockSymbolRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Finnhub WebSocket adapter for real-time US stock price ticks.
 *
 * <p>Only active when {@code finnhub.enabled=true} is set in application properties
 * AND {@code FINNHUB_API_KEY} is set in the environment. Both conditions must be
 * true for this component to be instantiated at all.
 *
 * <h3>How it integrates with the game engine</h3>
 * For multiplayer games using US (LIVE_US) symbols, {@link CandleService#getCurrentPrice}
 * reads from {@link #lastPrices} instead of the in-memory candle cache. This is the
 * only integration point — the settlement path is identical.
 *
 * <h3>Reconnect strategy</h3>
 * Exponential backoff: 1s → 2s → 4s → 8s → 16s → 30s (capped). Jitter ±15%.
 * On reconnect, all active symbol subscriptions are re-sent to Finnhub.
 *
 * <h3>Security</h3>
 * The API key is read from the environment variable {@code FINNHUB_API_KEY} via
 * {@code @Value("${finnhub.api.key:}")}. It is never logged or returned by any API.
 *
 * <h3>Educational disclaimer</h3>
 * Finnhub free tier is used for non-commercial, educational purposes.
 * This is clearly disclosed in the app footer per Finnhub's ToS requirements.
 */
@Component
@ConditionalOnProperty(name = "finnhub.enabled", havingValue = "true")
public class FinnhubWebSocketProvider {

    private static final Logger log = LoggerFactory.getLogger(FinnhubWebSocketProvider.class);
    private static final String WS_URL = "wss://ws.finnhub.io?token=";

    /** Maximum reconnect delay cap in milliseconds. */
    private static final long MAX_BACKOFF_MS = 30_000L;

    @Value("${finnhub.api.key:}")
    private String apiKey;

    private final StockSymbolRepository symbolRepo;
    private final ObjectMapper objectMapper;

    /** Latest price per symbol, updated on each Finnhub trade event. */
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "finnhub-reconnect");
                t.setDaemon(true);
                return t;
            });

    private final AtomicReference<WebSocket> wsRef = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile long backoffMs = 1_000L;

    public FinnhubWebSocketProvider(StockSymbolRepository symbolRepo, ObjectMapper objectMapper) {
        this.symbolRepo = symbolRepo;
        this.objectMapper = objectMapper;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @PostConstruct
    public void connect() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[Finnhub] finnhub.enabled=true but FINNHUB_API_KEY is not set — Finnhub disabled");
            return;
        }
        log.info("[Finnhub] Connecting to Finnhub WebSocket ...");
        doConnect();
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        scheduler.shutdownNow();
        WebSocket ws = wsRef.get();
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "server shutdown");
        }
        log.info("[Finnhub] WebSocket adapter shut down");
    }

    // ── Price access ───────────────────────────────────────────────────────

    /**
     * Returns the latest received price for the given symbol.
     * Returns {@code null} if no tick has been received yet.
     *
     * @param bareTicker e.g. {@code "AAPL"}
     */
    public Double getLastPrice(String bareTicker) {
        return lastPrices.get(bareTicker.toUpperCase());
    }

    /**
     * Returns true if a live price is available for the given ticker.
     */
    public boolean hasPriceFor(String bareTicker) {
        return lastPrices.containsKey(bareTicker.toUpperCase());
    }

    // ── Connection ─────────────────────────────────────────────────────────

    private void doConnect() {
        if (!running.get()) return;

        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create(WS_URL + apiKey);

        client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, new FinnhubListener())
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        log.error("[Finnhub] Connection failed: {} — retry in {}ms", err.getMessage(), backoffMs);
                        scheduleReconnect();
                    } else {
                        log.info("[Finnhub] Connected");
                        wsRef.set(ws);
                        backoffMs = 1_000L; // reset backoff on success
                        subscribeAll(ws);
                    }
                });
    }

    private void scheduleReconnect() {
        if (!running.get()) return;
        long jitter = (long) (backoffMs * 0.15 * (Math.random() * 2 - 1)); // ±15%
        long delay = Math.max(1_000L, backoffMs + jitter);
        scheduler.schedule(this::doConnect, delay, TimeUnit.MILLISECONDS);
        backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
    }

    private void subscribeAll(WebSocket ws) {
        List<StockSymbol> symbols = symbolRepo.findActiveLiveUsSymbols();
        for (StockSymbol sym : symbols) {
            String msg = "{\"type\":\"subscribe\",\"symbol\":\"" + sym.getBareTicker() + "\"}";
            ws.sendText(msg, true);
            log.debug("[Finnhub] Subscribed to {}", sym.getBareTicker());
        }
        log.info("[Finnhub] Subscribed to {} US symbols", symbols.size());
    }

    // ── WebSocket listener ─────────────────────────────────────────────────

    private class FinnhubListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            ws.request(1);
            if (last) {
                handleMessage(buffer.toString());
                buffer.setLength(0);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.warn("[Finnhub] WebSocket closed: code={}, reason='{}' — scheduling reconnect",
                    statusCode, reason);
            if (running.get()) {
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.error("[Finnhub] WebSocket error: {} — scheduling reconnect", error.getMessage());
            if (running.get()) {
                scheduleReconnect();
            }
        }

        @Override
        public void onOpen(WebSocket ws) {
            log.debug("[Finnhub] WebSocket opened");
            ws.request(1);
        }
    }

    // ── Message handling ───────────────────────────────────────────────────

    /**
     * Parses Finnhub trade events and updates the price map.
     *
     * <p>Finnhub trade message format:
     * <pre>
     * {
     *   "type": "trade",
     *   "data": [
     *     { "s": "AAPL", "p": 175.34, "t": 1722680012345, "v": 100 },
     *     ...
     *   ]
     * }
     * </pre>
     */
    private void handleMessage(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String type = root.path("type").asText();

            if ("trade".equals(type)) {
                JsonNode data = root.path("data");
                if (data.isArray()) {
                    for (JsonNode trade : data) {
                        String symbol = trade.path("s").asText();
                        double price  = trade.path("p").asDouble();
                        long   volume = trade.path("v").asLong();
                        long   ts     = trade.path("t").asLong();

                        if (!symbol.isBlank() && price > 0) {
                            lastPrices.put(symbol.toUpperCase(), price);
                            log.trace("[Finnhub] Tick: {}={} (vol={}, ts={})", symbol, price, volume, ts);
                        }
                    }
                }
            } else if ("ping".equals(type)) {
                // Finnhub sends pings — no action needed, onText already called ws.request(1)
                log.trace("[Finnhub] Received ping");
            } else if ("error".equals(type)) {
                log.error("[Finnhub] Server error message: {}", json);
            }
        } catch (Exception e) {
            log.warn("[Finnhub] Failed to parse message: {} — raw: {}", e.getMessage(),
                    json.length() > 200 ? json.substring(0, 200) + "..." : json);
        }
    }
}
