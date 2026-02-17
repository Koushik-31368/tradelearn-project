package com.tradelearn.server.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.repository.GameRepository;

/**
 * Manages server-authoritative candle data for 1v1 matches.
 *
 * Candles are loaded from a JSON file on the classpath
 * (e.g. classpath:candles/sample.json) and cached per game.
 * The current candle index is persisted in the Game entity so
 * price truth never originates from the frontend.
 */
@Service
public class CandleService {

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

    /** In-memory cache: gameId → loaded candle list */
    private final Map<Long, List<Candle>> candleCache = new ConcurrentHashMap<>();

    public CandleService(GameRepository gameRepository, ObjectMapper objectMapper) {
        this.gameRepository = gameRepository;
        this.objectMapper = objectMapper;
    }

    // ==================== LOAD CANDLES ====================

    /**
     * Load candle data for a game from the classpath JSON file.
     * Currently uses a single sample file; can be extended to
     * load per-symbol files (e.g. candles/{stockSymbol}.json).
     *
     * Also initialises totalCandles on the Game entity.
     */
    @Transactional
    public List<Candle> loadCandles(long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        // Determine which file to load (fall back to sample.json)
        String symbol = game.getStockSymbol().toUpperCase().trim();
        String path = "candles/" + symbol + ".json";

        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) {
            // Fall back to sample data
            path = "candles/sample.json";
            is = getClass().getClassLoader().getResourceAsStream(path);
        }
        if (is == null) {
            throw new IllegalStateException("No candle data found for symbol: " + symbol);
        }

        try {
            List<Candle> candles = objectMapper.readValue(is, new TypeReference<List<Candle>>() {});
            if (candles.isEmpty()) {
                throw new IllegalStateException("Candle file is empty for symbol: " + symbol);
            }

            // Persist total candle count and reset index
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
        return candleCache.computeIfAbsent(gameId, id -> loadCandles(id));
    }

    // ==================== CURRENT PRICE ====================

    /**
     * Returns the close price of the current candle for the given game.
     * This is the server-authoritative price used for all trades.
     */
    public double getCurrentPrice(long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

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
     */
    @Transactional
    public Candle advanceCandle(long gameId) {
        Game game = gameRepository.findById(gameId)
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
