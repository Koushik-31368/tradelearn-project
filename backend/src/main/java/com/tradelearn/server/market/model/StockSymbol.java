package com.tradelearn.server.market.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity for the {@code stock_symbols} table.
 *
 * <p>Acts as the authoritative registry for all supported symbols —
 * both NSE replay mode (sourced from yfinance / Neon) and US live mode
 * (sourced from Finnhub WebSocket).
 *
 * <p>The {@link #ticker} field uses the yfinance form (e.g. {@code "RELIANCE.NS"}).
 * The {@link #bareTicker} field is the bare form stored in {@code games.stock_symbol}
 * (e.g. {@code "RELIANCE"}) so the two can be correlated without a suffix transform.
 */
@Entity
@Table(name = "stock_symbols")
public class StockSymbol {

    public enum DataMode {
        REPLAY,   // NSE historical candles replayed from DB
        LIVE_US   // US real-time ticks from Finnhub WebSocket
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** yfinance / Finnhub ticker, e.g. {@code "RELIANCE.NS"} or {@code "AAPL"}. */
    @Column(name = "ticker", nullable = false, unique = true, length = 20)
    private String ticker;

    /**
     * Bare ticker matching {@code games.stock_symbol}, e.g. {@code "RELIANCE"}.
     * For US symbols the bare ticker equals the full ticker (e.g. {@code "AAPL"}).
     */
    @Column(name = "bare_ticker", nullable = false, unique = true, length = 20)
    private String bareTicker;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "exchange", nullable = false, length = 10)
    private String exchange = "NSE";

    @Enumerated(EnumType.STRING)
    @Column(name = "data_mode", nullable = false, length = 10)
    private DataMode dataMode = DataMode.REPLAY;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_ingested_at")
    private LocalDateTime lastIngestedAt;

    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getBareTicker() { return bareTicker; }
    public void setBareTicker(String bareTicker) { this.bareTicker = bareTicker; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public DataMode getDataMode() { return dataMode; }
    public void setDataMode(DataMode dataMode) { this.dataMode = dataMode; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getLastIngestedAt() { return lastIngestedAt; }
    public void setLastIngestedAt(LocalDateTime lastIngestedAt) { this.lastIngestedAt = lastIngestedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
