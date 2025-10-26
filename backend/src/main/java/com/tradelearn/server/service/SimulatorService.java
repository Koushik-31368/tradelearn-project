package com.tradelearn.server.service;

import com.tradelearn.server.dto.TradeRequest;
import com.tradelearn.server.model.Holding;
import com.tradelearn.server.model.Portfolio;
import com.tradelearn.server.model.Trade;
import com.tradelearn.server.repository.PortfolioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SimulatorService {

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private TradeService tradeService;

    @Transactional
    public Portfolio executeTrade(TradeRequest tradeRequest) throws Exception {
        Portfolio portfolio = portfolioRepository.findByUserId(tradeRequest.getUserId())
                .orElseThrow(() -> new Exception("Portfolio not found for user"));

        double tradeValue = tradeRequest.getPrice() * tradeRequest.getQuantity();
        String stockSymbol = tradeRequest.getStockSymbol();
        int quantity = tradeRequest.getQuantity();
        Optional<Holding> existingHoldingOpt = portfolio.getHoldings().stream()
                .filter(h -> h.getStockSymbol().equals(stockSymbol))
                .findFirst();
        String tradeType = tradeRequest.getTradeType().toUpperCase();

        if ("BUY".equals(tradeType)) {
            if (portfolio.getVirtualCash() < tradeValue) throw new Exception("Insufficient funds.");
            portfolio.setVirtualCash(portfolio.getVirtualCash() - tradeValue);
            Holding holding = existingHoldingOpt.orElse(new Holding(portfolio, stockSymbol, 0, 0.0));
            if (holding.getQuantity() < 0) throw new Exception("You have an open short position. Use 'Cover' to close it.");
            double existingValue = holding.getAveragePurchasePrice() * holding.getQuantity();
            int newQuantity = holding.getQuantity() + quantity;
            double newAveragePrice = (existingValue + tradeValue) / newQuantity;
            holding.setQuantity(newQuantity);
            holding.setAveragePurchasePrice(newAveragePrice);
            if (!existingHoldingOpt.isPresent()) portfolio.getHoldings().add(holding);

        } else if ("SELL".equals(tradeType)) {
            Holding holding = existingHoldingOpt.orElseThrow(() -> new Exception("No long position found for symbol: " + stockSymbol));
            if (holding.getQuantity() < quantity) throw new Exception("Insufficient shares to sell. You only own " + holding.getQuantity());
            portfolio.setVirtualCash(portfolio.getVirtualCash() + tradeValue);
            int newQuantity = holding.getQuantity() - quantity;
            if (newQuantity == 0) portfolio.getHoldings().remove(holding);
            else holding.setQuantity(newQuantity);

        } else if ("SHORT".equals(tradeType)) {
            portfolio.setVirtualCash(portfolio.getVirtualCash() + tradeValue);
            Holding holding = existingHoldingOpt.orElse(new Holding(portfolio, stockSymbol, 0, 0.0));
            if (holding.getQuantity() > 0) throw new Exception("You have an open long position. Sell your shares first.");
            double existingValue = holding.getAveragePurchasePrice() * Math.abs(holding.getQuantity());
            int newShortQuantity = holding.getQuantity() - quantity;
            double newAveragePrice = (existingValue + tradeValue) / Math.abs(newShortQuantity);
            holding.setQuantity(newShortQuantity);
            holding.setAveragePurchasePrice(newAveragePrice);
            if (!existingHoldingOpt.isPresent()) portfolio.getHoldings().add(holding);

        } else if ("COVER".equals(tradeType)) {
            Holding holding = existingHoldingOpt.orElseThrow(() -> new Exception("No short position found for symbol: " + stockSymbol));
            if (holding.getQuantity() >= 0) throw new Exception("You do not have a short position to cover.");
            if (portfolio.getVirtualCash() < tradeValue) throw new Exception("Insufficient funds to cover your short position.");
            if (Math.abs(holding.getQuantity()) < quantity) throw new Exception("You are only short " + Math.abs(holding.getQuantity()) + " shares.");
            portfolio.setVirtualCash(portfolio.getVirtualCash() - tradeValue);
            int newQuantity = holding.getQuantity() + quantity;
            if (newQuantity == 0) portfolio.getHoldings().remove(holding);
            else holding.setQuantity(newQuantity);

        } else {
            throw new Exception("Invalid trade type.");
        }

        Trade trade = new Trade();
        trade.setUserId(tradeRequest.getUserId());
        trade.setSymbol(stockSymbol);
        trade.setName(tradeRequest.getName());
        trade.setType(tradeType);
        trade.setQuantity(quantity);
        trade.setPrice(tradeRequest.getPrice());
        trade.setTimestamp(LocalDateTime.now());
        tradeService.createTrade(trade);

        return portfolioRepository.save(portfolio);
    }
}
