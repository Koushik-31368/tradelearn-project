// src/features/simulator/components/SimulatorDashboard.jsx
import React, {
  useState, useCallback, useMemo, useEffect, useRef,
} from 'react';
import { useSearchParams } from 'react-router-dom';
import PortfolioSummary    from './PortfolioSummary';
import Watchlist           from './Watchlist';
import CandlestickChart    from './CandlestickChart';
import OrderTicket         from './OrderTicket';
import AnalyticsDashboard  from './AnalyticsDashboard';
import TransactionHistory  from './TransactionHistory';
import MarketSentiment     from './MarketSentiment';
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

// ── Constants ──────────────────────────────────────────────────────────────
const STRATEGY_NAMES = {
  'rsi-reversion':    'RSI Mean Reversion',
  'sma-cross':        'SMA Crossover',
  'breakout':         'Breakout Trading',
  'momentum-trading': 'Momentum Trading',
  'support-resistance':'Support & Resistance',
  'scalping':         'Scalping',
  'buy-hold':         'Buy & Hold',
  'macd-strategy':    'MACD Strategy',
};

const REPLAY_SPEEDS = [
  { label: '1×', ms: 1500 },
  { label: '2×', ms: 750  },
  { label: '5×', ms: 300  },
];

// ── Component ──────────────────────────────────────────────────────────────
const SimulatorDashboard = () => {
  const [searchParams]   = useSearchParams();
  const strategySlug     = searchParams.get('strategy');
  const strategyName     = strategySlug ? STRATEGY_NAMES[strategySlug] || strategySlug : null;

  // ── Core state ─────────────────────────────────────────────────────────
  const [stocks]       = useState(() => getDailyStocks());
  const [selectedSymbol, setSelectedSymbol] = useState('RELIANCE');
  const [portfolio,    setPortfolio] = useState(() => getPortfolio());
  const [trades,       setTrades]    = useState(() => getTradeHistory());
  const [equityCurve]  = useState(() => generateEquityCurve());

  // ── Candle state ────────────────────────────────────────────────────────
  const [allCandles,   setAllCandles]   = useState([]);   // full dataset
  const [visibleCount, setVisibleCount] = useState(30);   // how many shown
  const [isLoading,    setIsLoading]    = useState(true);
  const [dataSource,   setDataSource]   = useState('live'); // 'live' | 'local' | 'scenario'
  const [scenario,     setScenario]     = useState('trending');

  // ── Replay state ────────────────────────────────────────────────────────
  const [isPlaying,  setIsPlaying]  = useState(false);
  const [speedIdx,   setSpeedIdx]   = useState(0);
  const replayTimer  = useRef(null);

  // ── UI state ────────────────────────────────────────────────────────────
  const [activeTab,  setActiveTab]  = useState('history');
  const [showReset,  setShowReset]  = useState(false);
  const [, setTick] = useState(0);

  // ── Load candles ────────────────────────────────────────────────────────
  useEffect(() => {
    setIsLoading(true);
    setIsPlaying(false);
    clearInterval(replayTimer.current);

    if (dataSource === 'local') {
      const candles = generateCandleHistory(selectedSymbol, 90);
      setAllCandles(candles);
      setVisibleCount(30);
      setIsLoading(false);
      return;
    }

    if (dataSource === 'scenario') {
      const candles = generateScenarioCandleHistory(selectedSymbol, scenario, 90);
      setAllCandles(candles);
      setVisibleCount(30);
      setIsLoading(false);
      return;
    }

    // dataSource === 'live' — try the real API, fall back to local
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
          // API returned nothing useful — fall back silently
          const candles = generateCandleHistory(selectedSymbol, 90);
          setAllCandles(candles);
          setVisibleCount(30);
          setDataSource('local');
        }
      })
      .catch(() => {
        // Network/backend error — fall back to local data
        const candles = generateCandleHistory(selectedSymbol, 90);
        setAllCandles(candles);
        setVisibleCount(30);
        setDataSource('local');
      })
      .finally(() => setIsLoading(false));

  }, [selectedSymbol, dataSource, scenario]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Live replay ticker ──────────────────────────────────────────────────
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

  // ── Derived data ────────────────────────────────────────────────────────
  const selectedStock   = useMemo(
    () => stocks.find((s) => s.symbol === selectedSymbol) || { symbol: selectedSymbol, price: 0, name: '', change: 0 },
    [stocks, selectedSymbol],
  );

  const visibleCandles  = useMemo(() => allCandles.slice(0, visibleCount), [allCandles, visibleCount]);
  const smaData         = useMemo(() => visibleCandles.length > 0 ? computeSMA(visibleCandles, 7) : [], [visibleCandles]);

  // Update selected stock price from latest candle if available
  const livePrice = visibleCandles.length > 0 ? visibleCandles[visibleCandles.length - 1].close : selectedStock.price;
  const liveStock = useMemo(() => ({ ...selectedStock, price: livePrice }), [selectedStock, livePrice]);

  // ── Handlers ────────────────────────────────────────────────────────────
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
    const fresh = resetPortfolio();
    setPortfolio(fresh);
    setTrades([]);
    setShowReset(false);
    setTick((t) => t + 1);
  }, []);

  const handleReplay = useCallback(() => {
    if (visibleCount >= allCandles.length) {
      setVisibleCount(10); // restart
      setTimeout(() => setIsPlaying(true), 50);
    } else {
      setIsPlaying((p) => !p);
    }
  }, [visibleCount, allCandles.length]);

  const handleSeek = useCallback((n) => {
    setVisibleCount(Math.max(10, Math.min(n, allCandles.length)));
  }, [allCandles.length]);

  // ── Render ──────────────────────────────────────────────────────────────
  return (
    <div className="sim-dashboard">
      <ReflectionModal userId={1} onReflectionsComplete={() => setTick((t) => t + 1)} />

      {/* ── Top bar ── */}
      <div className="sim-dashboard__topbar">
        <div className="sim-dashboard__topbar-left">
          <span className="sim-dashboard__title">
            📊 Simulator
            {dataSource !== 'live' && (
              <span className="sim-dashboard__source-badge">
                {dataSource === 'local' ? '🔌 Offline Data' : '🎬 Scenario'}
              </span>
            )}
          </span>
          {strategyName && (
            <span className="sim-dashboard__strategy-pill">🎯 {strategyName}</span>
          )}
        </div>

        <div className="sim-dashboard__topbar-right">
          {/* Data source selector */}
          <div className="sim-dashboard__source-row">
            <label className="sim-dashboard__source-label">Data:</label>
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
            <select
              className="sim-dashboard__scenario-select"
              value={scenario}
              onChange={(e) => setScenario(e.target.value)}
            >
              {SCENARIOS.map((s) => (
                <option key={s.key} value={s.key}>{s.label} — {s.desc}</option>
              ))}
            </select>
          )}

          <button
            className="sim-dashboard__reset-btn"
            onClick={() => setShowReset(true)}
            title="Reset portfolio to ₹10,00,000"
          >
            ↺ Reset
          </button>
        </div>
      </div>

      {/* Reset confirm */}
      {showReset && (
        <div className="sim-dashboard__confirm-bar">
          <span>Reset portfolio to ₹10,00,000? All trades will be erased.</span>
          <button className="sim-dashboard__confirm-yes" onClick={handleReset}>Yes, Reset</button>
          <button className="sim-dashboard__confirm-no"  onClick={() => setShowReset(false)}>Cancel</button>
        </div>
      )}

      {/* ── Portfolio summary ── */}
      <PortfolioSummary portfolio={portfolio} stocks={stocks} equityCurve={equityCurve} />

      {/* ── Main 3-column layout ── */}
      <div className="sim-dashboard__main">

        {/* Left — Watchlist */}
        <aside className="sim-dashboard__left">
          <Watchlist stocks={stocks} selectedSymbol={selectedSymbol} onSelect={handleSelect} />
        </aside>

        {/* Center — Chart + Replay controls */}
        <section className="sim-dashboard__center">
          {/* Replay controls */}
          <div className="sim-dashboard__replay-bar">
            <button
              className={`sim-dashboard__play-btn${isPlaying ? ' sim-dashboard__play-btn--playing' : ''}`}
              onClick={handleReplay}
              disabled={isLoading}
              aria-label={isPlaying ? 'Pause replay' : 'Play replay'}
            >
              {isPlaying ? '⏸ Pause' : visibleCount >= allCandles.length ? '↺ Restart' : '▶ Play'}
            </button>

            <div className="sim-dashboard__speed-group">
              {REPLAY_SPEEDS.map((s, i) => (
                <button
                  key={s.label}
                  className={`sim-dashboard__speed-btn${speedIdx === i ? ' sim-dashboard__speed-btn--active' : ''}`}
                  onClick={() => setSpeedIdx(i)}
                >
                  {s.label}
                </button>
              ))}
            </div>

            {/* Seek slider */}
            <input
              type="range"
              className="sim-dashboard__seek"
              min={10}
              max={allCandles.length || 90}
              value={visibleCount}
              onChange={(e) => { setIsPlaying(false); handleSeek(Number(e.target.value)); }}
              disabled={isLoading || allCandles.length === 0}
              aria-label="Candle timeline scrubber"
            />
            <span className="sim-dashboard__candle-count">
              {visibleCount}/{allCandles.length} candles
            </span>
          </div>

          <CandlestickChart
            candles={visibleCandles}
            smaData={smaData}
            symbol={selectedSymbol}
            isLoading={isLoading}
          />
        </section>

        {/* Right — Sentiment + Order Ticket */}
        <aside className="sim-dashboard__right">
          <MarketSentiment candles={visibleCandles} />
          <OrderTicket stock={liveStock} portfolio={portfolio} onTrade={handleTrade} />
        </aside>
      </div>

      {/* ── Bottom — tabs ── */}
      <div className="sim-dashboard__bottom">
        <div className="sim-dashboard__tab-row">
          {[
            { key: 'history',   label: '📋 Trade History' },
            { key: 'analytics', label: '📈 Analytics'     },
          ].map((t) => (
            <button
              key={t.key}
              className={`sim-dashboard__tab-btn${activeTab === t.key ? ' sim-dashboard__tab-btn--active' : ''}`}
              onClick={() => setActiveTab(t.key)}
            >
              {t.label}
            </button>
          ))}
        </div>

        {activeTab === 'history' ? (
          <TransactionHistory trades={trades} />
        ) : (
          <AnalyticsDashboard userId={1} />
        )}
      </div>
    </div>
  );
};

export default SimulatorDashboard;
