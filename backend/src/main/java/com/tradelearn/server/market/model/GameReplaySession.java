package com.tradelearn.server.market.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for the {@code game_replay_sessions} table.
 *
 * <p>Links a game to a specific date window of historical candle data.
 * This decouples "which market segment does this game replay" from the
 * {@code games} table, leaving {@code Game.java} and its optimistic-lock
 * {@code @Version} column entirely untouched.
 *
 * <p>Created by {@link ReplaySessionService#createSession} when a match
 * transitions to ACTIVE. The {@link CandleService} queries this record
 * to decide whether to load candles from the DB or fall back to a
 * classpath JSON file (for backwards-compatible demo/test mode).
 */
@Entity
@Table(name = "game_replay_sessions")
public class GameReplaySession {

    @Id
    @Column(name = "game_id")
    private Long gameId;

    /** yfinance-form ticker, e.g. {@code "RELIANCE.NS"}. */
    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    /** Candle resolution (currently always "daily"; "5min" reserved for future). */
    @Column(name = "resolution", nullable = false, length = 10)
    private String resolution = "daily";

    /** Inclusive start date of the historical window being replayed. */
    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    /** Inclusive end date of the historical window being replayed. */
    @Column(name = "window_end", nullable = false)
    private LocalDate windowEnd;

    /** Number of candles in the window (snapshot for quick sanity checks). */
    @Column(name = "candle_count", nullable = false)
    private int candleCount;

    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getGameId() { return gameId; }
    public void setGameId(Long gameId) { this.gameId = gameId; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public LocalDate getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDate windowStart) { this.windowStart = windowStart; }

    public LocalDate getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDate windowEnd) { this.windowEnd = windowEnd; }

    public int getCandleCount() { return candleCount; }
    public void setCandleCount(int candleCount) { this.candleCount = candleCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
