package com.tradelearn.server.market.service;

import com.tradelearn.server.market.model.GameReplaySession;
import com.tradelearn.server.market.model.StockSymbol;
import com.tradelearn.server.market.repository.GameReplaySessionRepository;
import com.tradelearn.server.market.repository.StockCandleDailyRepository;
import com.tradelearn.server.market.repository.StockSymbolRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Creates and retrieves {@link GameReplaySession} records.
 *
 * <p>Called by {@code MatchLifecycleService} when a multiplayer game transitions
 * to ACTIVE. Picks a random historical window of the requested length from the
 * available candle data for the game's symbol. The selected window is persisted
 * in {@code game_replay_sessions} and later read by {@link CandleService} to
 * load the correct candle slice from {@code stock_candles_daily}.
 *
 * <p>If no DB data is available for the symbol (e.g. local dev without ingestion),
 * {@code createSession} returns {@code Optional.empty()} and {@code CandleService}
 * falls back to its classpath JSON behaviour.
 */
@Service
public class ReplaySessionService {

    private static final Logger log = LoggerFactory.getLogger(ReplaySessionService.class);

    /**
     * Minimum number of trading days required to start a DB-backed replay session.
     * If fewer candles are available, the service falls back to JSON.
     */
    @SuppressWarnings("unused") // documents the threshold; used implicitly via fallback logic
    private static final int MIN_CANDLES_REQUIRED = 30;

    private final StockSymbolRepository symbolRepo;
    private final StockCandleDailyRepository candleRepo;
    private final GameReplaySessionRepository sessionRepo;

    public ReplaySessionService(StockSymbolRepository symbolRepo,
                                StockCandleDailyRepository candleRepo,
                                GameReplaySessionRepository sessionRepo) {
        this.symbolRepo = symbolRepo;
        this.candleRepo = candleRepo;
        this.sessionRepo = sessionRepo;
    }

    /**
     * Creates a replay session for the given game, picking a random window of
     * {@code requestedCandles} trading days from the available data for the symbol.
     *
     * @param gameId            The game to attach the session to.
     * @param bareTicker        The bare stock symbol, e.g. {@code "RELIANCE"}.
     * @param requestedCandles  How many candles the match will use (e.g. 30).
     * @return                  The created session, or {@code Optional.empty()} if
     *                          insufficient DB data is available (triggers JSON fallback).
     */
    @Transactional
    public Optional<GameReplaySession> createSession(long gameId, String bareTicker, int requestedCandles) {
        // Resolve yfinance ticker from bare ticker
        Optional<StockSymbol> symbolOpt = symbolRepo.findByBareTicker(bareTicker.toUpperCase());
        if (symbolOpt.isEmpty()) {
            log.warn("[ReplaySession] No symbol registration for bare ticker '{}' — falling back to JSON", bareTicker);
            return Optional.empty();
        }

        StockSymbol symbol = symbolOpt.get();
        if (symbol.getDataMode() != StockSymbol.DataMode.REPLAY) {
            // LIVE_US symbols don't use DB candles
            log.debug("[ReplaySession] Symbol '{}' is LIVE_US — no replay session needed", bareTicker);
            return Optional.empty();
        }

        String yfinanceTicker = symbol.getTicker();

        // Get available date range
        LocalDate minDate = candleRepo.findMinDateForTicker(yfinanceTicker);
        LocalDate maxDate = candleRepo.findMaxDateForTicker(yfinanceTicker);

        if (minDate == null || maxDate == null) {
            log.warn("[ReplaySession] No candle data in DB for ticker '{}' — run the ingestion script first", yfinanceTicker);
            return Optional.empty();
        }

        // Find a random window: pick a random start date such that [start, start+90days]
        // contains at least requestedCandles trading days.
        // We search over a calendar window of ~1.5× the requested candles to allow for weekends/holidays.
        int calendarDaysNeeded = requestedCandles + (requestedCandles / 2) + 14;
        long totalCalendarDays = minDate.until(maxDate, java.time.temporal.ChronoUnit.DAYS);

        if (totalCalendarDays < calendarDaysNeeded) {
            log.warn("[ReplaySession] Not enough history for '{}': have {} calendar days, need ~{}",
                    yfinanceTicker, totalCalendarDays, calendarDaysNeeded);
            return Optional.empty();
        }

        // Try up to 5 random windows to find one with enough trading days
        for (int attempt = 0; attempt < 5; attempt++) {
            long randomOffset = ThreadLocalRandom.current().nextLong(totalCalendarDays - calendarDaysNeeded);
            LocalDate windowStart = minDate.plusDays(randomOffset);
            LocalDate windowEnd = windowStart.plusDays(calendarDaysNeeded);
            if (windowEnd.isAfter(maxDate)) windowEnd = maxDate;

            long count = candleRepo.countByTickerAndDateRange(yfinanceTicker, windowStart, windowEnd);

            if (count >= requestedCandles) {
                // We have enough candles — find the actual end date after exactly requestedCandles rows
                List<com.tradelearn.server.market.model.StockCandleDaily> slice =
                        candleRepo.findByTickerAndDateRange(yfinanceTicker, windowStart, windowEnd);
                LocalDate actualEnd = slice.get((int) requestedCandles - 1).getTradeDate();

                GameReplaySession session = new GameReplaySession();
                session.setGameId(gameId);
                session.setTicker(yfinanceTicker);
                session.setResolution("daily");
                session.setWindowStart(windowStart);
                session.setWindowEnd(actualEnd);
                session.setCandleCount((int) requestedCandles);

                sessionRepo.save(session);
                log.info("[ReplaySession] Created session for game {} — ticker={}, window={} → {}, candles={}",
                        gameId, yfinanceTicker, windowStart, actualEnd, requestedCandles);
                return Optional.of(session);
            }
        }

        log.warn("[ReplaySession] Could not find a valid window with {} candles for '{}' after 5 attempts",
                requestedCandles, yfinanceTicker);
        return Optional.empty();
    }

    /**
     * Retrieves an existing replay session for a game.
     * Returns {@code Optional.empty()} if no DB session was created (JSON fallback mode).
     */
    public Optional<GameReplaySession> getSession(long gameId) {
        return sessionRepo.findByGameId(gameId);
    }

    /**
     * Returns a list of all available NSE symbols with candle data,
     * including their available date ranges. Used by the API to populate
     * game creation dropdowns.
     */
    public List<SymbolInfo> listAvailableSymbols() {
        return symbolRepo.findActiveReplaySymbols().stream()
                .map(sym -> {
                    String ticker = sym.getTicker();
                    LocalDate min = candleRepo.findMinDateForTicker(ticker);
                    LocalDate max = candleRepo.findMaxDateForTicker(ticker);
                    return new SymbolInfo(
                            sym.getBareTicker(),
                            sym.getDisplayName(),
                            sym.getExchange(),
                            min,
                            max
                    );
                })
                .filter(info -> info.earliestDate() != null)
                .toList();
    }

    /**
     * Lightweight DTO for symbol metadata returned by the API.
     */
    public record SymbolInfo(
            String ticker,
            String displayName,
            String exchange,
            LocalDate earliestDate,
            LocalDate latestDate
    ) {}
}
