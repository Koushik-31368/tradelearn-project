package com.tradelearn.server.market.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JPA entity for the {@code stock_candles_daily} table.
 *
 * <p>One row = one trading day of OHLCV data for one symbol.
 * Sourced by the {@code scripts/ingest_market_data.py} ingestion script
 * via yfinance. The {@code (ticker, tradeDate)} pair is unique — the
 * script uses ON CONFLICT DO UPDATE so re-runs are safe.
 *
 * <p>Column names use {@code open_price / close_price} (not {@code open / close})
 * to avoid collision with SQL reserved words across dialects.
 */
@Entity
@Table(
    name = "stock_candles_daily",
    uniqueConstraints = @UniqueConstraint(columnNames = {"ticker", "trade_date"})
)
public class StockCandleDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 14, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "volume", nullable = false)
    private Long volume;

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }

    public BigDecimal getOpenPrice() { return openPrice; }
    public void setOpenPrice(BigDecimal openPrice) { this.openPrice = openPrice; }

    public BigDecimal getHighPrice() { return highPrice; }
    public void setHighPrice(BigDecimal highPrice) { this.highPrice = highPrice; }

    public BigDecimal getLowPrice() { return lowPrice; }
    public void setLowPrice(BigDecimal lowPrice) { this.lowPrice = lowPrice; }

    public BigDecimal getClosePrice() { return closePrice; }
    public void setClosePrice(BigDecimal closePrice) { this.closePrice = closePrice; }

    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }
}
