// src/features/simulator/components/SimulatorDashboard.jsx
import React, {
  useState, useCallback, useMemo, useEffect, useRef,
} from 'react';
import { useSearchParams } from 'react-router-dom';
import Watchlist           from './Watchlist';
import CandlestickChart    from './CandlestickChart';
import OrderTicket         from './OrderTicket';
import TransactionHistory  from './TransactionHistory';
import ReflectionModal     from './ReflectionModal';
import {
  getDailyStocks,
  generateCandleHistory,
  generateScenarioCandleHistory,
  computeSMA,
  getPortfolio,
  getTradeHistory,
  executeDemoTrade,
  resetPortfolio,
  SCENARIOS,
} from '../utils/simulatorData';

import './SimulatorDashboard.css';

/* ── Constants ── */
const STRATEGY_NAMES = {
  'rsi-reversion':     'RSI Mean Reversion',
  'sma-cross':         'SMA Crossover',
  'breakout':          'Breakout Trading',
  'momentum-trading':  'Momentum Trading',
  'support-resistance':'Support & Resistance',
  'scalping':          'Scalping',
  'buy-hold':          'Buy & Hold',
  'macd-strategy':     'MACD Strategy',
};

const REPLAY_SPEEDS = [
  { label: '1×', ms: 1500 },
  { label: '2×', ms: 750  },
  { label: '5×', ms: 300  },
];

/* ── Component ── */
const SimulatorDashboard = () => {
  const [searchParams]   = useSearchParams();
  const strategySlug     = searchParams.get('strategy');
  const strategyName     = strategySlug ? STRATEGY_NAMES[strategySlug] || strategySlug : null;

  const [stocks]       = useState(() => getDailyStocks());
  const [selectedSymbol, setSelectedSymbol] = useState('RELIANCE');
  const [portfolio,    setPortfolio] = useState(() => getPortfolio());
  const [trades,       setTrades]    = useState(() => getTradeHistory());

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

  // Portfolio stats
  const portfolioStats = useMemo(() => {
    const holdingsValue = Object.entries(portfolio.holdings).reduce((sum, [sym, h]) => {
      const s = stocks.find((x) => x.symbol === sym);
      return s ? sum + h.qty * s.price : sum;
    }, 0);
    const totalValue = portfolio.cash + holdingsValue;
    const pnl = totalValue - 1_000_000;
    const pnlPct = ((pnl / 1_000_000) * 100).toFixed(2);
    const positions = Object.keys(portfolio.holdings).length;
    return { totalValue, pnl, pnlPct, positions, cash: portfolio.cash };
  }, [portfolio, stocks]);

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

  const isProfit = portfolioStats.pnl >= 0;

  /* ── Render ── */
  return (
    <div className="sim-dash">
      <ReflectionModal userId={1} onReflectionsComplete={() => setTick((t) => t + 1)} />

      {/* ═══ Portfolio Strip ═══ */}
      <div className="sim-strip">
        <div className="sim-strip__left">
          <div className="sim-strip__item">
            <span className="sim-strip__label">Portfolio</span>
            <span className="sim-strip__val">₹{portfolioStats.totalValue.toLocaleString('en-IN', { maximumFractionDigits: 0 })}</span>
          </div>
          <div className="sim-strip__divider" />
          <div className="sim-strip__item">
            <span className="sim-strip__label">P&L</span>
            <span className={`sim-strip__val ${isProfit ? 'sim-strip__val--up' : 'sim-strip__val--down'}`}>
              {isProfit ? '+' : ''}{portfolioStats.pnlPct}%
            </span>
          </div>
          <div className="sim-strip__divider" />
          <div className="sim-strip__item">
            <span className="sim-strip__label">Cash</span>
            <span className="sim-strip__val">₹{portfolioStats.cash.toLocaleString('en-IN', { maximumFractionDigits: 0 })}</span>
          </div>
          <div className="sim-strip__divider" />
          <div className="sim-strip__item">
            <span className="sim-strip__label">Positions</span>
            <span className="sim-strip__val">{portfolioStats.positions}</span>
          </div>

          {strategyName && (
            <>
              <div className="sim-strip__divider" />
              <div className="sim-strip__item">
                <span className="sim-strip__label">Strategy</span>
                <span className="sim-strip__val sim-strip__val--accent">🎯 {strategyName}</span>
              </div>
            </>
          )}
        </div>

        <div className="sim-strip__right">
          <div className="sim-strip__source">
            {['live', 'local', 'scenario'].map((src) => (
              <button
                key={src}
                className={`sim-strip__src${dataSource === src ? ' sim-strip__src--on' : ''}`}
                onClick={() => setDataSource(src)}
              >
                {src === 'live' ? '🌐' : src === 'local' ? '💾' : '🎬'}
              </button>
            ))}
          </div>

          {dataSource === 'scenario' && (
            <select
              className="sim-strip__scenario"
              value={scenario}
              onChange={(e) => setScenario(e.target.value)}
            >
              {SCENARIOS.map((s) => (
                <option key={s.key} value={s.key}>{s.label}</option>
              ))}
            </select>
          )}

          <button className="sim-strip__reset" onClick={() => setShowReset(true)} title="Reset portfolio">↺</button>
        </div>
      </div>

      {/* Reset confirm */}
      {showReset && (
        <div className="sim-confirm">
          <span>Reset portfolio to ₹10L?</span>
          <button className="sim-confirm__yes" onClick={handleReset}>Yes</button>
          <button className="sim-confirm__no" onClick={() => setShowReset(false)}>Cancel</button>
        </div>
      )}

      {/* ═══ Main Layout ═══ */}
      <div className="sim-body">

        {/* LEFT — Watchlist */}
        <aside className="sim-body__watch">
          <Watchlist stocks={stocks} selectedSymbol={selectedSymbol} onSelect={handleSelect} />
        </aside>

        {/* CENTER — Chart */}
        <main className="sim-body__chart">
          <CandlestickChart
            candles={visibleCandles}
            smaData={smaData}
            symbol={selectedSymbol}
            isLoading={isLoading}
            /* Replay props — rendered inside chart header */
            isPlaying={isPlaying}
            onReplay={handleReplay}
            speedIdx={speedIdx}
            onSpeedChange={setSpeedIdx}
            speeds={REPLAY_SPEEDS}
            visibleCount={visibleCount}
            totalCandles={allCandles.length}
            onSeek={(n) => { setIsPlaying(false); setVisibleCount(Math.max(10, Math.min(n, allCandles.length))); }}
          />
        </main>

        {/* RIGHT — Order Ticket */}
        <aside className="sim-body__order">
          <OrderTicket stock={liveStock} portfolio={portfolio} onTrade={handleTrade} />
        </aside>
      </div>

      {/* ═══ Bottom — Trade History ═══ */}
      <TransactionHistory trades={trades} />
    </div>
  );
};

export default SimulatorDashboard;
