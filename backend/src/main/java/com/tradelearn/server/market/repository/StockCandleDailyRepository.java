package com.tradelearn.server.market.repository;

import com.tradelearn.server.market.model.StockCandleDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockCandleDailyRepository extends JpaRepository<StockCandleDaily, Long> {

    /**
     * Returns all candles for the given ticker between {@code from} and {@code to} (inclusive),
     * ordered chronologically. This is the primary slice query used by {@code CandleService}
     * when loading candles for a DB-backed replay session.
     *
     * <p>The covering index {@code idx_scd_ticker_date} on {@code (ticker, trade_date)}
     * makes this an index-range scan — O(log n + k) where k is the result set size.
     */
    @Query("SELECT c FROM StockCandleDaily c " +
           "WHERE c.ticker = :ticker " +
           "AND c.tradeDate >= :from " +
           "AND c.tradeDate <= :to " +
           "ORDER BY c.tradeDate ASC")
    List<StockCandleDaily> findByTickerAndDateRange(
            @Param("ticker") String ticker,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Returns the earliest and latest available trade_date for a given ticker.
     * Used by {@link com.tradelearn.server.market.service.ReplaySessionService}
     * to select a valid random window.
     */
    @Query("SELECT MIN(c.tradeDate) FROM StockCandleDaily c WHERE c.ticker = :ticker")
    LocalDate findMinDateForTicker(@Param("ticker") String ticker);

    @Query("SELECT MAX(c.tradeDate) FROM StockCandleDaily c WHERE c.ticker = :ticker")
    LocalDate findMaxDateForTicker(@Param("ticker") String ticker);

    /**
     * Returns the count of available candles for the ticker in the given window.
     * Used to verify that a proposed replay window has enough data.
     */
    @Query("SELECT COUNT(c) FROM StockCandleDaily c " +
           "WHERE c.ticker = :ticker " +
           "AND c.tradeDate >= :from " +
           "AND c.tradeDate <= :to")
    long countByTickerAndDateRange(
            @Param("ticker") String ticker,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
