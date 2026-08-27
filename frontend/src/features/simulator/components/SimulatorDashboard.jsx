// src/features/simulator/components/SimulatorDashboard.jsx
import React, {
  useState, useCallback, useMemo, useEffect, useRef,
} from 'react';
import { useSearchParams } from 'react-router-dom';
import Watchlist           from './Watchlist';
import CandlestickChart    from './CandlestickChart';
import PortfolioSummary    from './PortfolioSummary';
import OrderTicket         from './OrderTicket';
import TransactionHistory  from './TransactionHistory';
import ReflectionModal     from './ReflectionModal';
import {
  getDailyStocks,
  generateCandleHistory,
  generateScenarioCandleHistory,
  generateEquityCurve,
  computeSMA,
  getPortfolio,
  getTradeHistory,
  executeDemoTrade,
  resetPortfolio,
  SCENARIOS,
} from '../utils/simulatorData';

import './SimulatorDashboard.css';

const STRATEGY_NAMES = {
  'rsi-reversion': 'RSI Mean Reversion', 'sma-cross': 'SMA Crossover',
  'breakout': 'Breakout Trading', 'momentum-trading': 'Momentum Trading',
  'support-resistance': 'Support & Resistance', 'scalping': 'Scalping',
  'buy-hold': 'Buy & Hold', 'macd-strategy': 'MACD Strategy',
};

const REPLAY_SPEEDS = [
  { label: '1×', ms: 1500 },
  { label: '2×', ms: 750  },
  { label: '5×', ms: 300  },
];

const SimulatorDashboard = () => {
  const [searchParams]   = useSearchParams();
  const strategySlug     = searchParams.get('strategy');
  const strategyName     = strategySlug ? STRATEGY_NAMES[strategySlug] || strategySlug : null;

  const [stocks]       = useState(() => getDailyStocks());
  const [selectedSymbol, setSelectedSymbol] = useState('RELIANCE');
  const [portfolio,    setPortfolio] = useState(() => getPortfolio());
  const [trades,       setTrades]    = useState(() => getTradeHistory());
  const [equityCurve]  = useState(() => generateEquityCurve());

  const [allCandles,   setAllCandles]   = useState([]);
  const [visibleCount, setVisibleCount] = useState(30);
  const [isLoading,    setIsLoading]    = useState(true);
  const [dataSource,   setDataSource]   = useState('live');
  const [scenario,     setScenario]     = useState('trending');

  const [isPlaying,  setIsPlaying]  = useState(false);
  const [speedIdx,   setSpeedIdx]   = useState(0);
  const replayTimer  = useRef(null);

  const [showReset,  setShowReset]  = useState(false);
  const [, setTick] = useState(0);

  /* ── Load candles ── */
  useEffect(() => {
    setIsLoading(true);
    setIsPlaying(false);
    clearInterval(replayTimer.current);

    if (dataSource === 'local') {
      setAllCandles(generateCandleHistory(selectedSymbol, 90));
      setVisibleCount(30);
      setIsLoading(false);
      return;
    }
    if (dataSource === 'scenario') {
      setAllCandles(generateScenarioCandleHistory(selectedSymbol, scenario, 90));
      setVisibleCount(30);
      setIsLoading(false);
      return;
    }

    import('../../../api/market.api')
      .then(({ fetchMarketHistory }) => {
        const end   = new Date().toISOString().slice(0, 10);
        const start = new Date(Date.now() - 90 * 86_400_000).toISOString().slice(0, 10);
        return fetchMarketHistory(selectedSymbol, start, end);
      })
      .then((data) => {
        if (data && data.length > 5) {
          setAllCandles(data);
          setVisibleCount(30);
        } else {
          setAllCandles(generateCandleHistory(selectedSymbol, 90));
          setVisibleCount(30);
          setDataSource('local');
        }
      })
      .catch(() => {
        setAllCandles(generateCandleHistory(selectedSymbol, 90));
        setVisibleCount(30);
        setDataSource('local');
      })
      .finally(() => setIsLoading(false));
  }, [selectedSymbol, dataSource, scenario]); // eslint-disable-line react-hooks/exhaustive-deps

  /* ── Live replay ── */
  useEffect(() => {
    if (!isPlaying) { clearInterval(replayTimer.current); return; }
    if (visibleCount >= allCandles.length) { setIsPlaying(false); return; }
    replayTimer.current = setInterval(() => {
      setVisibleCount((c) => {
        if (c >= allCandles.length) { setIsPlaying(false); clearInterval(replayTimer.current); return c; }
        return c + 1;
      });
    }, REPLAY_SPEEDS[speedIdx].ms);
    return () => clearInterval(replayTimer.current);
  }, [isPlaying, speedIdx, allCandles.length]); // eslint-disable-line react-hooks/exhaustive-deps

  /* ── Derived ── */
  const selectedStock = useMemo(
    () => stocks.find((s) => s.symbol === selectedSymbol) || { symbol: selectedSymbol, price: 0, name: '', change: 0 },
    [stocks, selectedSymbol],
  );
  const visibleCandles = useMemo(() => allCandles.slice(0, visibleCount), [allCandles, visibleCount]);
  const smaData        = useMemo(() => visibleCandles.length > 0 ? computeSMA(visibleCandles, 7) : [], [visibleCandles]);
  const livePrice      = visibleCandles.length > 0 ? visibleCandles[visibleCandles.length - 1].close : selectedStock.price;
  const liveStock      = useMemo(() => ({ ...selectedStock, price: livePrice }), [selectedStock, livePrice]);

  /* ── Handlers ── */
  const handleSelect = useCallback((symbol) => {
    setSelectedSymbol(symbol);
    setIsPlaying(false);
  }, []);

  const handleTrade = useCallback((tradeParams) => {
    const result = executeDemoTrade({ ...tradeParams, price: livePrice });
    if (result.success) {
      setPortfolio(result.portfolio);
      setTrades(getTradeHistory());
      setTick((t) => t + 1);
    }
    return result;
  }, [livePrice]);

  const handleReset = useCallback(() => {
    setPortfolio(resetPortfolio());
    setTrades([]);
    setShowReset(false);
    setTick((t) => t + 1);
  }, []);

  const handleReplay = useCallback(() => {
    if (visibleCount >= allCandles.length) {
      setVisibleCount(10);
      setTimeout(() => setIsPlaying(true), 50);
    } else {
      setIsPlaying((p) => !p);
    }
  }, [visibleCount, allCandles.length]);

  return (
    <div className="sim-dashboard">
      <ReflectionModal userId={1} onReflectionsComplete={() => setTick((t) => t + 1)} />

      {/* ── Controls Bar ── */}
      <div className="sim-dashboard__topbar">
        <div className="sim-dashboard__topbar-left">
          <h2 className="sim-dashboard__title">
            📈 Simulator
            {strategyName && <span className="sim-dashboard__strategy">🎯 {strategyName}</span>}
          </h2>
        </div>
        <div className="sim-dashboard__topbar-right">
          <div className="sim-dashboard__sources">
            {['live', 'local', 'scenario'].map((src) => (
              <button
                key={src}
                className={`sim-dashboard__src-btn${dataSource === src ? ' sim-dashboard__src-btn--active' : ''}`}
                onClick={() => setDataSource(src)}
              >
                {src === 'live' ? '🌐 Live' : src === 'local' ? '💾 Local' : '🎬 Scenario'}
              </button>
            ))}
          </div>
          {dataSource === 'scenario' && (
            <select className="sim-dashboard__scenario-select" value={scenario} onChange={(e) => setScenario(e.target.value)}>
              {SCENARIOS.map((s) => <option key={s.key} value={s.key}>{s.label}</option>)}
            </select>
          )}
          <button className="sim-dashboard__reset-btn" onClick={() => setShowReset(true)}>↺ Reset</button>
        </div>
      </div>

      {showReset && (
        <div className="sim-dashboard__confirm-bar">
          <span>Reset portfolio to ₹10,00,000?</span>
          <button className="sim-dashboard__confirm-yes" onClick={handleReset}>Yes, Reset</button>
          <button className="sim-dashboard__confirm-no" onClick={() => setShowReset(false)}>Cancel</button>
        </div>
      )}

      {/* ── Portfolio Cards ── */}
      <PortfolioSummary portfolio={portfolio} stocks={stocks} equityCurve={equityCurve} />

      {/* ── Main 3-Column ── */}
      <div className="sim-dashboard__main">
        {/* Watchlist */}
        <div className="sim-dashboard__left">
          <Watchlist stocks={stocks} selectedSymbol={selectedSymbol} onSelect={handleSelect} />
        </div>

        {/* Chart with inline replay */}
        <div className="sim-dashboard__center">
          <CandlestickChart
            candles={visibleCandles}
            smaData={smaData}
            symbol={selectedSymbol}
            isLoading={isLoading}
            isPlaying={isPlaying}
            onReplay={handleReplay}
            speedIdx={speedIdx}
            onSpeedChange={setSpeedIdx}
            speeds={REPLAY_SPEEDS}
            visibleCount={visibleCount}
            totalCandles={allCandles.length}
            onSeek={(n) => { setIsPlaying(false); setVisibleCount(Math.max(10, Math.min(n, allCandles.length))); }}
          />
        </div>

        {/* Order Ticket */}
        <div className="sim-dashboard__right">
          <OrderTicket stock={liveStock} portfolio={portfolio} onTrade={handleTrade} />
        </div>
      </div>

      {/* ── Bottom: Trade History ── */}
      <TransactionHistory trades={trades} />
    </div>
  );
};

export default SimulatorDashboard;
