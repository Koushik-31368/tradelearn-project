package com.tradelearn.server.market.repository;

import com.tradelearn.server.market.model.StockSymbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockSymbolRepository extends JpaRepository<StockSymbol, Long> {

    Optional<StockSymbol> findByTicker(String ticker);

    Optional<StockSymbol> findByBareTicker(String bareTicker);

    List<StockSymbol> findByActiveTrue();

    @Query("SELECT s FROM StockSymbol s WHERE s.active = true AND s.dataMode = 'REPLAY'")
    List<StockSymbol> findActiveReplaySymbols();

    @Query("SELECT s FROM StockSymbol s WHERE s.active = true AND s.dataMode = 'LIVE_US'")
    List<StockSymbol> findActiveLiveUsSymbols();
}
