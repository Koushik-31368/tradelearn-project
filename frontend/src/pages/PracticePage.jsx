// src/pages/PracticePage.jsx
import React, { useEffect, useState, useRef, useCallback } from 'react';
import { fetchMarketHistory } from '../services/marketApi';
import CandlestickChart from '../components/simulator/CandlestickChart';
import './PracticePage.css';

// ── Stocks available for Practice Mode ──────────────────────────────────────
const PRACTICE_STOCKS = [
  { symbol: 'RELIANCE',  name: 'Reliance Industries', sector: 'Energy'  },
  { symbol: 'TCS',       name: 'Tata Consultancy',    sector: 'IT'      },
  { symbol: 'INFY',      name: 'Infosys Ltd',         sector: 'IT'      },
  { symbol: 'HDFCBANK',  name: 'HDFC Bank',           sector: 'Banking' },
  { symbol: 'ICICIBANK', name: 'ICICI Bank',          sector: 'Banking' },
  { symbol: 'WIPRO',     name: 'Wipro Ltd',           sector: 'IT'      },
  { symbol: 'SBIN',      name: 'State Bank of India', sector: 'Banking' },
  { symbol: 'ITC',       name: 'ITC Ltd',             sector: 'FMCG'   },
  { symbol: 'LT',        name: 'Larsen & Toubro',     sector: 'Infra'   },
  { symbol: 'MARUTI',    name: 'Maruti Suzuki',       sector: 'Auto'    },
];

// ── Replay speed options (ms per candle) ────────────────────────────────────
const SPEEDS = [
  { label: '0.5×',  ms: 3000 },
  { label: '1×',    ms: 1500 },
  { label: '2×',    ms:  750 },
  { label: '5×',    ms:  300 },
];

export default function PracticePage() {
  const [symbol,        setSymbol]        = useState('RELIANCE');
  const [allCandles,    setAllCandles]    = useState([]);
  const [visibleCount,  setVisibleCount]  = useState(0);
  const [isLoading,     setIsLoading]     = useState(false);
  const [error,         setError]         = useState(null);
  const [isPlaying,     setIsPlaying]     = useState(false);
  const [speedIdx,      setSpeedIdx]      = useState(1);   // default: 1×
  const [hasStarted,    setHasStarted]    = useState(false);

  const intervalRef = useRef(null);

  // ── Visible candle slice ──────────────────────────────────────────────────
  const visibleCandles = allCandles.slice(0, visibleCount);
  const progress       = allCandles.length > 0
    ? Math.round((visibleCount / allCandles.length) * 100)
    : 0;
  const lastCandle     = visibleCandles[visibleCandles.length - 1] || null;

  // ── Load data when symbol changes ────────────────────────────────────────
  const loadData = useCallback(async (sym) => {
    clearInterval(intervalRef.current);
    setIsPlaying(false);
    setHasStarted(false);
    setVisibleCount(0);
    setAllCandles([]);
    setError(null);
    setIsLoading(true);

    try {
      const data = await fetchMarketHistory(sym);
      if (data.length === 0) {
        setError('No market data available for this symbol right now. Try another stock or check back during market hours.');
        return;
      }
      setAllCandles(data);
      // Seed the first 10 candles immediately so the chart isn't blank
      setVisibleCount(Math.min(10, data.length));
    } catch (err) {
      setError(`Could not load data for ${sym}: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData(symbol);
    return () => clearInterval(intervalRef.current);
  }, [symbol, loadData]);

  // ── Replay tick ──────────────────────────────────────────────────────────
  useEffect(() => {
    clearInterval(intervalRef.current);
    if (!isPlaying || allCandles.length === 0) return;

    const speedMs = SPEEDS[speedIdx].ms;
    intervalRef.current = setInterval(() => {
      setVisibleCount((prev) => {
        if (prev >= allCandles.length) {
          clearInterval(intervalRef.current);
          setIsPlaying(false);
          return prev;
        }
        return prev + 1;
      });
    }, speedMs);

    return () => clearInterval(intervalRef.current);
  }, [isPlaying, speedIdx, allCandles.length]);

  // ── Controls ─────────────────────────────────────────────────────────────
  const handlePlayPause = () => {
    if (visibleCount >= allCandles.length && !isPlaying) {
      // Replay finished — reset and replay from beginning
      setVisibleCount(10);
    }
    setHasStarted(true);
    setIsPlaying((p) => !p);
  };

  const handleReset = () => {
    clearInterval(intervalRef.current);
    setIsPlaying(false);
    setHasStarted(false);
    setVisibleCount(Math.min(10, allCandles.length));
  };

  const handleReload = () => loadData(symbol);

  const finished = allCandles.length > 0 && visibleCount >= allCandles.length;

  // ── Last candle colour ────────────────────────────────────────────────────
  const candleColor = lastCandle
    ? (lastCandle.close >= lastCandle.open ? '#26a69a' : '#ef5350')
    : '#9ca3af';

  return (
    <div className="practice-page">

      {/* ── Header ── */}
      <div className="practice-header">
        <div className="practice-header__left">
          <h1 className="practice-header__title">
            <span className="practice-header__badge">LIVE DATA</span>
            Practice Mode
          </h1>
          <p className="practice-header__sub">
            Real 5-minute candles from NSE via Yahoo Finance, replayed at your chosen speed.
            Read the price action, then use the Simulator to trade with confidence.
          </p>
        </div>
      </div>

      {/* ── Controls bar ── */}
      <div className="practice-controls">

        {/* Stock selector */}
        <div className="practice-controls__group">
          <label className="practice-controls__label">Stock</label>
          <select
            className="practice-controls__select"
            value={symbol}
            onChange={(e) => setSymbol(e.target.value)}
            disabled={isLoading}
          >
            {PRACTICE_STOCKS.map((s) => (
              <option key={s.symbol} value={s.symbol}>
                {s.symbol} — {s.name}
              </option>
            ))}
          </select>
        </div>

        {/* Speed selector */}
        <div className="practice-controls__group">
          <label className="practice-controls__label">Speed</label>
          <div className="practice-controls__speed-btns">
            {SPEEDS.map((sp, i) => (
              <button
                key={sp.label}
                className={`speed-btn${speedIdx === i ? ' speed-btn--active' : ''}`}
                onClick={() => setSpeedIdx(i)}
              >
                {sp.label}
              </button>
            ))}
          </div>
        </div>

        {/* Play / Reset / Reload */}
        <div className="practice-controls__group practice-controls__group--actions">
          <button
            className={`practice-btn practice-btn--play${isPlaying ? ' practice-btn--pause' : ''}`}
            onClick={handlePlayPause}
            disabled={isLoading || error || allCandles.length === 0}
          >
            {isPlaying ? '⏸ Pause' : (finished ? '↺ Replay' : (hasStarted ? '▶ Resume' : '▶ Play'))}
          </button>
          <button
            className="practice-btn practice-btn--reset"
            onClick={handleReset}
            disabled={isLoading || allCandles.length === 0}
          >
            ↺ Reset
          </button>
          <button
            className="practice-btn practice-btn--reload"
            onClick={handleReload}
            disabled={isLoading}
          >
            ⟳ Refresh Data
          </button>
        </div>

      </div>

      {/* ── Progress bar ── */}
      {allCandles.length > 0 && (
        <div className="practice-progress">
          <div className="practice-progress__bar">
            <div
              className="practice-progress__fill"
              style={{ width: `${progress}%` }}
            />
          </div>
          <span className="practice-progress__label">
            {visibleCount} / {allCandles.length} candles ({progress}%)
            {finished && <span className="practice-progress__done"> · Done</span>}
          </span>
        </div>
      )}

      {/* ── OHLCV stats strip ── */}
      {lastCandle && (
        <div className="practice-ohlcv">
          <span className="practice-ohlcv__symbol" style={{ color: candleColor }}>
            {symbol}
          </span>
          <div className="practice-ohlcv__field">
            <span className="practice-ohlcv__key">O</span>
            <span className="practice-ohlcv__val">{lastCandle.open?.toFixed(2)}</span>
          </div>
          <div className="practice-ohlcv__field">
            <span className="practice-ohlcv__key">H</span>
            <span className="practice-ohlcv__val" style={{ color: '#26a69a' }}>{lastCandle.high?.toFixed(2)}</span>
          </div>
          <div className="practice-ohlcv__field">
            <span className="practice-ohlcv__key">L</span>
            <span className="practice-ohlcv__val" style={{ color: '#ef5350' }}>{lastCandle.low?.toFixed(2)}</span>
          </div>
          <div className="practice-ohlcv__field">
            <span className="practice-ohlcv__key">C</span>
            <span className="practice-ohlcv__val" style={{ color: candleColor }}>{lastCandle.close?.toFixed(2)}</span>
          </div>
          <div className="practice-ohlcv__field">
            <span className="practice-ohlcv__key">VOL</span>
            <span className="practice-ohlcv__val">{Number(lastCandle.volume)?.toLocaleString('en-IN')}</span>
          </div>
        </div>
      )}

      {/* ── Chart area ── */}
      <div className="practice-chart-wrapper">
        {isLoading && (
          <div className="practice-status">
            <div className="practice-spinner" />
            <p>Loading market data for <strong>{symbol}</strong>…</p>
          </div>
        )}

        {!isLoading && error && (
          <div className="practice-status practice-status--error">
            <p className="practice-status__icon">⚠</p>
            <p>{error}</p>
            <button className="practice-btn practice-btn--reload" onClick={handleReload}>
              Try Again
            </button>
          </div>
        )}

        {!isLoading && !error && allCandles.length > 0 && (
          <CandlestickChart
            candles={visibleCandles}
            liveMode={false}
            symbol={symbol}
          />
        )}

        {!isLoading && !error && allCandles.length === 0 && (
          <div className="practice-status">
            <p>No candles loaded. Select a stock and press ▶ Play.</p>
          </div>
        )}
      </div>

      {/* ── Info footer ── */}
      <div className="practice-info">
        <div className="practice-info__card">
          <h4>How Practice Mode works</h4>
          <ul>
            <li>Real 5-minute OHLCV candles fetched live from Yahoo Finance (NSE).</li>
            <li>Candles are revealed one-by-one at your chosen speed — just like watching a live market.</li>
            <li>Press <strong>Pause</strong> at any point to study the price structure.</li>
            <li>No real money — ideal for pattern recognition training before live play.</li>
          </ul>
        </div>
        <div className="practice-info__card">
          <h4>Tips</h4>
          <ul>
            <li>Use <strong>1×</strong> speed to feel the market rhythm.</li>
            <li>Look for support / resistance levels as candles form.</li>
            <li>Try to call the next move before resuming — then check yourself.</li>
            <li>Switch to <strong>Simulator Mode</strong> to test your strategy with fake orders.</li>
          </ul>
        </div>
      </div>

    </div>
  );
}
