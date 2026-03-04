// src/components/simulator/SimulatorDashboard.jsx
// Main simulator dashboard — orchestrates all sub-components.
import React, { useState, useCallback, useMemo, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import PortfolioSummary from './PortfolioSummary';
import Watchlist from './Watchlist';
import CandlestickChart from './CandlestickChart';
import TradingPanel from './TradingPanel';
import PerformanceChart from './PerformanceChart';
import TransactionHistory from './TransactionHistory';
import MarketSentiment from './MarketSentiment';
import {
  getDailyStocks,
  generateEquityCurve,
  computeSMA,
  getPortfolio,
  getTradeHistory,
  executeDemoTrade,
} from '../../utils/simulatorData';
import { generateInitialCandles } from '../../utils/marketSimulator';
import { backendUrl } from '../../utils/api';
import './SimulatorDashboard.css';

/* ── Strategy slug → display name mapping ── */
const STRATEGY_NAMES = {
  'rsi-reversion': 'RSI Mean Reversion',
  'sma-cross': 'SMA Crossover',
  'breakout': 'Breakout Trading',
  'momentum-trading': 'Momentum Trading',
  'support-resistance': 'Support & Resistance',
  'scalping': 'Scalping',
  'buy-hold': 'Buy & Hold',
  'macd-strategy': 'MACD Strategy',
};

const SimulatorDashboard = () => {
  const [searchParams] = useSearchParams();
  const strategySlug = searchParams.get('strategy');
  const strategyName = strategySlug ? STRATEGY_NAMES[strategySlug] || strategySlug : null;

  const [stocks] = useState(() => getDailyStocks());
  const [selectedSymbol, setSelectedSymbol] = useState(stocks[0]?.symbol || '');
  const [portfolio, setPortfolio] = useState(() => getPortfolio());
  const [trades, setTrades] = useState(() => getTradeHistory());
  const [, setTick] = useState(0); // force re-render after trades

  // ── Real market base price (fetched from backend on symbol change) ───────
  const [realBasePrice, setRealBasePrice] = useState(null);

  useEffect(() => {
    if (!selectedSymbol) return;
    setRealBasePrice(null); // reset while loading
    fetch(backendUrl(`/api/market/price?symbol=${encodeURIComponent(selectedSymbol)}`))
      .then((res) => res.ok ? res.json() : Promise.reject(res))
      .then((data) => {
        if (data && data.price > 0) {
          setRealBasePrice(data.price);
        }
      })
      .catch(() => {
        // Non-critical — fall back to static base price from simulatorData
      });
  }, [selectedSymbol]);

  const equityCurve = useMemo(() => generateEquityCurve(), []);

  const selectedStock = useMemo(
    () => stocks.find((s) => s.symbol === selectedSymbol) || null,
    [stocks, selectedSymbol]
  );

  // Use real market price as the base when available; fall back to daily-seeded price.
  const effectiveBasePrice = realBasePrice ?? selectedStock?.price ?? 1400;

  const candles = useMemo(
    () => selectedSymbol ? generateInitialCandles(effectiveBasePrice, 30) : [],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [selectedSymbol, effectiveBasePrice]
  );

  const smaData = useMemo(() => (candles.length > 0 ? computeSMA(candles, 7) : []), [candles]);

  const handleSelect = useCallback((symbol) => {
    setSelectedSymbol(symbol);
  }, []);

  const handleTrade = useCallback(
    (tradeParams) => {
      const result = executeDemoTrade(tradeParams);
      if (result.success) {
        setPortfolio(result.portfolio);
        setTrades(getTradeHistory());
        setTick((t) => t + 1);
      }
      return result;
    },
    []
  );

  return (
    <div className="sim-dashboard">
      {/* Strategy Mode Banner */}
      {strategyName && (
        <div className="sim-dashboard__strategy-banner">
          <span className="sim-dashboard__strategy-icon">🎯</span>
          <span className="sim-dashboard__strategy-label">
            Strategy Mode: <strong>{strategyName}</strong>
          </span>
          <span className="sim-dashboard__strategy-hint">
            Practice this strategy with simulated trades below
          </span>
        </div>
      )}

      {/* Top — Portfolio Summary Cards */}
      <PortfolioSummary portfolio={portfolio} stocks={stocks} equityCurve={equityCurve} />

      {/* Main 3-Column Layout */}
      <div className="sim-dashboard__main">
        {/* Left — Watchlist */}
        <aside className="sim-dashboard__left">
          <Watchlist stocks={stocks} selectedSymbol={selectedSymbol} onSelect={handleSelect} />
        </aside>

        {/* Center — Chart */}
        <section className="sim-dashboard__center">
          <CandlestickChart
            candles={candles}
            smaData={smaData}
            symbol={selectedSymbol}
            basePrice={selectedStock?.price}
            liveMode={true}
          />
        </section>

        {/* Right — Sentiment + Trading Panel */}
        <aside className="sim-dashboard__right">
          <MarketSentiment candles={candles} />
          <TradingPanel stock={selectedStock} portfolio={portfolio} onTrade={handleTrade} />
        </aside>
      </div>

      {/* Bottom — Performance + History */}
      <div className="sim-dashboard__bottom">
        <div className="sim-dashboard__bottom-left">
          <PerformanceChart data={equityCurve} />
        </div>
        <div className="sim-dashboard__bottom-right">
          <TransactionHistory trades={trades} />
        </div>
      </div>
    </div>
  );
};

export default SimulatorDashboard;
