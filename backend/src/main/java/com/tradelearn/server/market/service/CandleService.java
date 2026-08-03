package com.tradelearn.server.market.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradelearn.server.game.model.Game;
import com.tradelearn.server.game.repository.GameRepository;
import com.tradelearn.server.market.model.GameReplaySession;
import com.tradelearn.server.market.model.StockCandleDaily;
import com.tradelearn.server.market.model.StockSymbol;
import com.tradelearn.server.market.provider.FinnhubWebSocketProvider;
import com.tradelearn.server.market.repository.GameReplaySessionRepository;
import com.tradelearn.server.market.repository.StockCandleDailyRepository;
import com.tradelearn.server.market.repository.StockSymbolRepository;

/**
 * Manages server-authoritative candle data for 1v1 matches.
 *
 * <h3>Data source priority</h3>
 * <ol>
 *   <li><b>DB-backed (preferred):</b> If a {@link GameReplaySession} exists for the
 *       game, candles are loaded from {@code stock_candles_daily} in Neon PostgreSQL.
 *       This path is taken after the ingestion script has populated the DB.</li>
 *   <li><b>Classpath JSON (fallback):</b> If no replay session exists (local dev /
 *       demo mode before ingestion runs), the original behaviour is preserved —
 *       candles are loaded from {@code classpath:candles/{SYMBOL}.json}.</li>
 * </ol>
 *
 * <p>The current candle index is persisted in the Game entity so price truth
 * never originates from the frontend. All downstream settlement logic
 * ({@code advanceCandle}, {@code getCurrentPrice}) is completely unchanged.
 */
@Service
public class CandleService {

    private static final Logger log = LoggerFactory.getLogger(CandleService.class);

    // ==================== CANDLE DTO ====================

    public static class Candle {
        private String date;
        private double open;
        private double high;
        private double low;
        private double close;
        private long volume;

        public Candle() {}

        public String getDate()   { return date; }
        public double getOpen()   { return open; }
        public double getHigh()   { return high; }
        public double getLow()    { return low; }
        public double getClose()  { return close; }
        public long   getVolume() { return volume; }

        public void setDate(String date)   { this.date = date; }
        public void setOpen(double open)   { this.open = open; }
        public void setHigh(double high)   { this.high = high; }
        public void setLow(double low)     { this.low = low; }
        public void setClose(double close) { this.close = close; }
        public void setVolume(long volume) { this.volume = volume; }
    }

    // ==================== DEPENDENCIES ====================

    private final GameRepository gameRepository;
    private final ObjectMapper objectMapper;
    private final GameReplaySessionRepository replaySessionRepo;
    private final StockCandleDailyRepository candleDailyRepo;
    private final StockSymbolRepository symbolRepo;

    /**
     * Optional — only present when {@code finnhub.enabled=true}.
     * Used to return real-time prices for LIVE_US games.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private FinnhubWebSocketProvider finnhub;

    /** In-memory cache: gameId → loaded candle list */
    private final Map<Long, List<Candle>> candleCache = new ConcurrentHashMap<>();

    public CandleService(GameRepository gameRepository,
                         ObjectMapper objectMapper,
                         GameReplaySessionRepository replaySessionRepo,
                         StockCandleDailyRepository candleDailyRepo,
                         StockSymbolRepository symbolRepo) {
        this.gameRepository = gameRepository;
        this.objectMapper = objectMapper;
        this.replaySessionRepo = replaySessionRepo;
        this.candleDailyRepo = candleDailyRepo;
        this.symbolRepo = symbolRepo;
    }

    // ==================== LOAD CANDLES ====================

    /**
     * Load candle data for a game from the classpath JSON file.
     * Currently uses a single sample file; can be extended to
     * load per-symbol files (e.g. candles/{stockSymbol}.json).
     *
     * Also initialises totalCandles on the Game entity.
     *
     * Idempotent: if candles are already cached, returns immediately.
     * This prevents the double-load race where a second call would
     * reset currentCandleIndex to 0 mid-game.
     */
    @Transactional
    public List<Candle> loadCandles(long gameId) {
        // Fast path: already loaded — do NOT re-read from disk / reset index
        List<Candle> cached = candleCache.get(gameId);
        if (cached != null) return cached;

        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        // Double-check after acquiring lock
        cached = candleCache.get(gameId);
        if (cached != null) return cached;

        // ── PATH 1: DB-backed replay session (preferred) ─────────────────────────
        Optional<GameReplaySession> sessionOpt = replaySessionRepo.findByGameId(gameId);
        if (sessionOpt.isPresent()) {
            return loadCandlesFromDb(gameId, game, sessionOpt.get());
        }

        // ── PATH 2: Classpath JSON fallback (demo / local dev) ────────────────────
        log.debug("[CandleService] No replay session for game {} — using classpath JSON fallback", gameId);
        return loadCandlesFromJson(gameId, game);
    }

    /**
     * Loads candles from the {@code stock_candles_daily} table using the
     * date window defined in the {@link GameReplaySession}.
     */
    private List<Candle> loadCandlesFromDb(long gameId, Game game, GameReplaySession session) {
        List<StockCandleDaily> dbRows = candleDailyRepo.findByTickerAndDateRange(
                session.getTicker(), session.getWindowStart(), session.getWindowEnd());

        if (dbRows.isEmpty()) {
            log.warn("[CandleService] DB returned 0 candles for game {} (ticker={}, window={} → {}). "
                    + "Falling back to JSON.",
                    gameId, session.getTicker(), session.getWindowStart(), session.getWindowEnd());
            return loadCandlesFromJson(gameId, game);
        }

        List<Candle> candles = dbRows.stream()
                .map(row -> {
                    Candle c = new Candle();
                    c.setDate(row.getTradeDate().toString());
                    c.setOpen(row.getOpenPrice().doubleValue());
                    c.setHigh(row.getHighPrice().doubleValue());
                    c.setLow(row.getLowPrice().doubleValue());
                    c.setClose(row.getClosePrice().doubleValue());
                    c.setVolume(row.getVolume());
                    return c;
                })
                .toList();

        log.info("[CandleService] Loaded {} candles from DB for game {} (ticker={})",
                candles.size(), gameId, session.getTicker());

        game.setTotalCandles(candles.size());
        game.setCurrentCandleIndex(0);
        gameRepository.save(game);

        candleCache.put(gameId, candles);
        return candles;
    }

    /**
     * Original classpath JSON load path, preserved for backwards compatibility.
     * Used when no replay session exists (local dev / demo stocks without DB data).
     */
    private List<Candle> loadCandlesFromJson(long gameId, Game game) {
        String symbol = game.getStockSymbol().toUpperCase().trim();
        String path = "candles/" + symbol + ".json";

        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) {
            path = "candles/sample.json";
            is = getClass().getClassLoader().getResourceAsStream(path);
        }
        if (is == null) {
            throw new IllegalStateException("No candle data found for symbol: " + symbol
                    + ". Either run the ingestion script or provide candles/" + symbol + ".json");
        }

        try {
            List<Candle> candles = objectMapper.readValue(is, new TypeReference<List<Candle>>() {});
            if (candles.isEmpty()) {
                throw new IllegalStateException("Candle file is empty for symbol: " + symbol);
            }

            game.setTotalCandles(candles.size());
            game.setCurrentCandleIndex(0);
            gameRepository.save(game);

            candleCache.put(gameId, candles);
            return candles;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse candle JSON: " + e.getMessage(), e);
        }
    }

    // ==================== GET CANDLES (lazy-load) ====================

    /**
     * Returns the cached candle list, loading it if necessary.
     */
    public List<Candle> getCandles(long gameId) {
        List<Candle> cached = candleCache.get(gameId);
        if (cached != null) return cached;
        return loadCandles(gameId);   // loadCandles puts result into candleCache itself
    }

    // ==================== CURRENT PRICE ====================

    /**
     * Returns the close price of the current candle for the given game.
     * This is the server-authoritative price used for all trades.
     *
     * <p><b>LIVE_US mode:</b> If the game's symbol is LIVE_US and Finnhub has
     * published at least one tick, the Finnhub live price is returned instead
     * of the candle-cache price. This makes US game pricing genuinely real-time.
     * If no Finnhub tick has been received yet, falls through to the candle cache
     * (which will be empty, so an error is thrown — the caller should check before
     * starting a US game that Finnhub is connected).
     */
    public double getCurrentPrice(long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        // ── LIVE_US path: read from Finnhub if available ──
        if (finnhub != null) {
            String bareTicker = game.getStockSymbol().toUpperCase();
            StockSymbol sym = symbolRepo.findByBareTicker(bareTicker).orElse(null);
            if (sym != null && sym.getDataMode() == StockSymbol.DataMode.LIVE_US) {
                Double livePrice = finnhub.getLastPrice(bareTicker);
                if (livePrice != null) {
                    return livePrice;
                }
                throw new IllegalStateException(
                    "Finnhub has not yet published a price for " + bareTicker
                    + ". Wait for the first tick before trading.");
            }
        }

        // ── REPLAY path: read from in-memory candle cache ──
        List<Candle> candles = getCandles(gameId);
        int index = game.getCurrentCandleIndex();

        if (index < 0 || index >= candles.size()) {
            throw new IllegalStateException(
                    String.format("Candle index %d out of range [0, %d)", index, candles.size())
            );
        }

        return candles.get(index).getClose();
    }

    /**
     * Returns the current candle (full OHLCV) for the given game.
     */
    public Candle getCurrentCandle(long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        List<Candle> candles = getCandles(gameId);
        int index = game.getCurrentCandleIndex();

        if (index < 0 || index >= candles.size()) {
            throw new IllegalStateException(
                    String.format("Candle index %d out of range [0, %d)", index, candles.size())
            );
        }

        return candles.get(index);
    }

    // ==================== ADVANCE CANDLE ====================

    /**
     * Advances to the next candle. Returns the new Candle,
     * or null if the game has exhausted all candles.
     *
     * Uses PESSIMISTIC_WRITE lock to prevent the candle-skip race:
     * if two ticks overlap, the second blocks until the first commits,
     * then reads the already-incremented index — no candle is skipped.
     */
    @Transactional
    public Candle advanceCandle(long gameId) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        List<Candle> candles = getCandles(gameId);
        int nextIndex = game.getCurrentCandleIndex() + 1;

        if (nextIndex >= candles.size()) {
            return null; // no more candles — caller should end the match
        }

        game.setCurrentCandleIndex(nextIndex);
        gameRepository.save(game);

        return candles.get(nextIndex);
    }

    // ==================== QUERIES ====================

    public boolean hasMoreCandles(long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));
        return game.getCurrentCandleIndex() < game.getTotalCandles() - 1;
    }

    public int getRemainingCandles(long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));
        return game.getTotalCandles() - game.getCurrentCandleIndex() - 1;
    }

    /**
     * Evict cached candles for a finished game to free memory.
     */
    public void evict(long gameId) {
        candleCache.remove(gameId);
    }
}
