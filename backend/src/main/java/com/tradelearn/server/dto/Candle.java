package com.tradelearn.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Canonical OHLCV candle DTO — single representation used across all services.
 *
 * <p>The {@code date} field is an ISO-8601 date string (e.g. {@code "2024-01-15"})
 * produced by {@link com.tradelearn.server.market.provider.YahooFinanceProvider}.
 * Use {@link #getLocalDate()} when you need a {@link LocalDate} for sorting or
 * date arithmetic (e.g. in {@link com.tradelearn.server.analytics.service.BacktestService}).
 *
 * <p>Previously, a separate {@code CandleDto} class with a {@link LocalDate}
 * field existed alongside this class — creating import confusion and divergence.
 * That class has been deleted; all backtest and market-data code now uses this
 * canonical DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candle {

    /** ISO-8601 date string, e.g. {@code "2024-01-15"}. Never null. */
    private String date;

    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;

    // ── Convenience helpers ──────────────────────────────────────────────────

    /**
     * Parse {@link #date} as a {@link LocalDate}.
     *
     * <p>Accepts both {@code "yyyy-MM-dd"} (ISO) and {@code "yyyy/MM/dd"} formats.
     * Returns {@link LocalDate#MIN} (1 Jan 1970) as a safe fallback if the date
     * string is absent or malformed, so that sort-by-date operations still behave
     * deterministically rather than throwing.
     *
     * @return the parsed date, or {@link LocalDate#EPOCH} on parse failure
     */
    public LocalDate getLocalDate() {
        if (date == null || date.isBlank()) {
            return LocalDate.EPOCH;
        }
        try {
            return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            } catch (DateTimeParseException e2) {
                return LocalDate.EPOCH;
            }
        }
    }
}
