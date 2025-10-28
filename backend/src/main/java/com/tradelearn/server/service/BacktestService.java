// src/main/java/com/tradelearn/server/service/BacktestService.java
package com.tradelearn.server.service;

import com.tradelearn.server.dto.BacktestRequest;
import com.tradelearn.server.dto.BacktestResult;
import com.tradelearn.server.dto.BatchBacktestRequest;
import com.tradelearn.server.dto.BatchBacktestResult;
import com.tradelearn.server.dto.CandleDto;
import com.tradelearn.server.dto.EquityPointDto;
import com.tradelearn.server.dto.TradeDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class BacktestService {

    public BacktestResult runSmaCross(BacktestRequest req) {
        if (req == null || req.getCandles() == null || req.getCandles().isEmpty()) {
            throw new IllegalArgumentException("Candles are required");
        }
        if (req.getSmaFast() <= 0 || req.getSmaSlow() <= 0 || req.getSmaFast() >= req.getSmaSlow()) {
            throw new IllegalArgumentException("Invalid SMA settings");
        }
        if (req.getInitialCapital() <= 0) {
            throw new IllegalArgumentException("Initial capital must be > 0");
        }

        // Copy and sort candles by date ascending
        List<CandleDto> candles = new ArrayList<>(req.getCandles());
        candles.sort(Comparator.comparing(CandleDto::getDate));

        int fast = req.getSmaFast();
        int slow = req.getSmaSlow();

        // Build close array
        final int n = candles.size();
        double[] closes = new double[n];
        for (int i = 0; i < n; i++) closes[i] = candles.get(i).getClose();

        // Compute SMAs
        double[] smaFast = sma(closes, fast);
        double[] smaSlow = sma(closes, slow);

        // Backtest state
        double cash = req.getInitialCapital();
        int position = 0;           // shares
        double entryPrice = 0.0;

        List<TradeDto> trades = new ArrayList<>();
        List<EquityPointDto> equity = new ArrayList<>();

        int wins = 0;
        int closed = 0;

        double peak = cash;         // for drawdown
        double maxDrawdown = 0.0;

        for (int i = 0; i < n; i++) {
            CandleDto c = candles.get(i);
            double price = c.getClose();

            // Generate signals only when we have both SMAs
            if (i > 0 && i >= slow) {
                double fPrev = smaFast[i - 1];
                double sPrev = smaSlow[i - 1];
                double fNow = smaFast[i];
                double sNow = smaSlow[i];

                boolean crossUp = (fPrev <= sPrev) && (fNow > sNow);
                boolean crossDown = (fPrev >= sPrev) && (fNow < sNow);

                // BUY on golden cross if flat
                if (crossUp && position == 0) {
                    int qty = (int) Math.floor(cash / price);
                    if (qty > 0) {
                        position = qty;
                        cash -= qty * price;
                        entryPrice = price;
                        trades.add(new TradeDto(c.getDate(), "BUY", price, qty));
                    }
                }

                // SELL on death cross if in position
                if (crossDown && position > 0) {
                    cash += position * price;
                    trades.add(new TradeDto(c.getDate(), "SELL", price, position));
                    if (price > entryPrice) wins++;
                    closed++;
                    position = 0;
                    entryPrice = 0.0;
                }
            }

            // Daily equity and drawdown
            double eq = cash + position * price;
            equity.add(new EquityPointDto(c.getDate(), eq));
            if (eq > peak) peak = eq;
            double dd = peak > 0 ? (peak - eq) / peak : 0.0;
            if (dd > maxDrawdown) maxDrawdown = dd;
        }

        // Liquidate at end if still in position
        if (position > 0) {
            CandleDto last = candles.get(n - 1);
            double price = last.getClose();
            cash += position * price;
            trades.add(new TradeDto(last.getDate(), "SELL", price, position));
            if (price > entryPrice) wins++;
            closed++;
            position = 0;
        }

        double finalCapital = cash;
        double retPct = (finalCapital - req.getInitialCapital()) / req.getInitialCapital() * 100.0;
        double winRate = closed > 0 ? (wins * 100.0 / closed) : 0.0;

        BacktestResult result = new BacktestResult();
        result.setSymbol(req.getSymbol());
        result.setInitialCapital(req.getInitialCapital());
        result.setFinalCapital(finalCapital);
        result.setReturnPct(round2(retPct));
        result.setMaxDrawdownPct(round2(maxDrawdown * 100.0));
        result.setWinRatePct(round2(winRate));
        result.setTradesCount(trades.size());
        result.setTrades(trades);
        result.setEquityCurve(equity);
        return result;
    }

    public BatchBacktestResult runBatch(BatchBacktestRequest req) {
    if (req.getSymbols() == null || req.getSymbols().isEmpty())
        throw new IllegalArgumentException("symbols required");

    List<BatchBacktestResult.Item> items = new ArrayList<>();
    for (String sym : req.getSymbols()) {
        BacktestRequest one = new BacktestRequest();
        one.setSymbol(sym);
        one.setInitialCapital(req.getInitialCapital());
        one.setSmaFast(req.getSmaFast());
        one.setSmaSlow(req.getSmaSlow());
        one.setCandles(req.getCandles());
        one.setOos(req.getOos());

        BacktestResult r = runSmaCross(one);

        BatchBacktestResult.Item it = new BatchBacktestResult.Item();
        it.setSymbol(sym);
        it.setReturnPct(r.getReturnPct());
        it.setMaxDrawdownPct(r.getMaxDrawdownPct());
        it.setWinRatePct(r.getWinRatePct());
        it.setTrades(r.getTradesCount());
        it.setFinalCapital(r.getFinalCapital());
        items.add(it);
    }
    // Sort by Return descending by default
    items.sort((a,b) -> Double.compare(b.getReturnPct(), a.getReturnPct()));

    BatchBacktestResult out = new BatchBacktestResult();
    out.setResults(items);
    return out;
}

    private static double[] sma(double[] x, int period) {
        int n = x.length;
        double[] out = new double[n];
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += x[i];
            if (i >= period) sum -= x[i - period];
            if (i >= period - 1) out[i] = sum / period;
            else out[i] = Double.NaN;
        }
        return out;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}