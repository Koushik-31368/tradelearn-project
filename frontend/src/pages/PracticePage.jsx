// src/pages/PracticePage.jsx
import React, { useEffect, useState, useRef, useCallback } from 'react';
import { fetchMarketHistory } from '../services/marketApi';
import CandlestickChart from '../components/simulator/CandlestickChart';
import { historicalEvents, findEvent } from '../data/historicalEvents';
import './PracticePage.css';

// ── Modes ──────────────────────────────────────────────────────────────────
const MODE_LIVE       = 'live';
const MODE_HISTORICAL = 'historical';

// ── Live Data: stocks available for recent 5-day replay ───────────────────
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
  // ── Mode ──────────────────────────────────────────────────────────────────
  const [mode,         setMode]         = useState(MODE_LIVE);

  // ── Live mode: selected stock ──────────────────────────────────────────
  const [symbol,       setSymbol]       = useState('RELIANCE');

  // ── Historical mode: selected event ──────────────────────────────────
  const [eventId,      setEventId]      = useState(historicalEvents[0].id);
  const activeEvent = findEvent(eventId);

  // ── Shared replay state ───────────────────────────────────────────────
  const [allCandles,   setAllCandles]   = useState([]);
  const [visibleCount, setVisibleCount] = useState(0);
  const [isLoading,    setIsLoading]    = useState(false);
  const [error,        setError]        = useState(null);
  const [isPlaying,    setIsPlaying]    = useState(false);
  const [speedIdx,     setSpeedIdx]     = useState(1);   // default: 1×
  const [hasStarted,   setHasStarted]   = useState(false);

  const intervalRef = useRef(null);

  // ── Derived display ───────────────────────────────────────────────────
  const visibleCandles = allCandles.slice(0, visibleCount);
  const progress       = allCandles.length > 0
    ? Math.round((visibleCount / allCandles.length) * 100)
    : 0;
  const lastCandle     = visibleCandles[visibleCandles.length - 1] || null;
  const displaySymbol  = mode === MODE_LIVE ? symbol : activeEvent.symbol;

  // ── Load data ─────────────────────────────────────────────────────────
  const loadData = useCallback(async (currentMode, sym, event) => {
    clearInterval(intervalRef.current);
    setIsPlaying(false);
    setHasStarted(false);
    setVisibleCount(0);
    setAllCandles([]);
    setError(null);
    setIsLoading(true);

    try {
      const data = currentMode === MODE_HISTORICAL
        ? await fetchMarketHistory(event.symbol, event.start, event.end)
        : await fetchMarketHistory(sym);

      if (data.length === 0) {
        setError(currentMode === MODE_HISTORICAL
          ? `No data available for "${event.title}". Yahoo Finance may not have this range.`
          : `No market data for ${sym} right now. Try another stock or check back during market hours.`
        );
        return;
      }
      setAllCandles(data);
      setVisibleCount(Math.min(10, data.length));
    } catch (err) {
      setError(`Could not load data: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Reload whenever mode, symbol, or event changes
  useEffect(() => {
    loadData(mode, symbol, activeEvent);
    return () => clearInterval(intervalRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, symbol, eventId]);

  // ── Replay tick ──────────────────────────────────────────────────────
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

  // ── Controls ──────────────────────────────────────────────────────────
  const handlePlayPause = () => {
    if (visibleCount >= allCandles.length && !isPlaying) {
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

  const handleReload = () => loadData(mode, symbol, activeEvent);

  const handleModeSwitch = (newMode) => {
    if (newMode === mode) return;
    setMode(newMode);
  };

  const finished = allCandles.length > 0 && visibleCount >= allCandles.length;

  // ── Last candle colour ────────────────────────────────────────────────
  const candleColor = lastCandle
    ? (lastCandle.close >= lastCandle.open ? '#26a69a' : '#ef5350')
    : '#9ca3af';

  return (
    <div className="practice-page">

      {/* ── Header ── */}
      <div className="practice-header">
        <div className="practice-header__left">
          <h1 className="practice-header__title">
            <span className={`practice-header__badge practice-header__badge--${mode}`}>
              {mode === MODE_LIVE ? 'LIVE DATA' : 'HISTORICAL'}
            </span>
            Practice Mode
          </h1>
          <p className="practice-header__sub">
            {mode === MODE_LIVE
              ? 'Real 5-minute NSE candles (last 5 trading days), replayed at your chosen speed.'
              : 'Replay iconic Indian market crashes and rallies using real historical data.'}
          </p>
        </div>
      </div>

      {/* ── Mode tabs ── */}
      <div className="practice-tabs">
        <button
          className={`practice-tab${mode === MODE_LIVE ? ' practice-tab--active' : ''}`}
          onClick={() => handleModeSwitch(MODE_LIVE)}
        >
          📈 Live Data
        </button>
        <button
          className={`practice-tab${mode === MODE_HISTORICAL ? ' practice-tab--active' : ''}`}
          onClick={() => handleModeSwitch(MODE_HISTORICAL)}
        >
          📚 Historical Events
        </button>
      </div>

      {/* ── Historical event cards (shown only in historical mode) ── */}
      {mode === MODE_HISTORICAL && (
        <div className="practice-events">
          {historicalEvents.map((ev) => (
            <button
              key={ev.id}
              className={`event-card event-card--${ev.type}${eventId === ev.id ? ' event-card--selected' : ''}`}
              onClick={() => setEventId(ev.id)}
              disabled={isLoading}
            >
              <div className="event-card__top">
                <span className={`event-tag event-tag--${ev.type}`}>
                  {ev.type === 'crash' ? '📉 Crash' : '📈 Rally'}
                </span>
                <span className="event-card__period">{ev.subtitle}</span>
              </div>
              <div className="event-card__title">{ev.title}</div>
              <div className="event-card__stock">{ev.symbol} · {ev.symbolName}</div>
              <div className="event-card__desc">{ev.description}</div>
            </button>
          ))}
        </div>
      )}

      {/* ── Controls bar ── */}
      <div className="practice-controls">

        {/* Live mode: stock picker */}
        {mode === MODE_LIVE && (
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
        )}

        {/* Historical mode: event summary pill */}
        {mode === MODE_HISTORICAL && (
          <div className="practice-controls__group">
            <label className="practice-controls__label">Selected Event</label>
            <div className="practice-event-pill">
              <span className={`event-tag event-tag--${activeEvent.type}`}>
                {activeEvent.type === 'crash' ? '📉' : '📈'}
              </span>
              <strong>{activeEvent.title}</strong>
              <span className="practice-event-pill__sym">{activeEvent.symbol}</span>
              <span className="practice-event-pill__period">{activeEvent.subtitle}</span>
            </div>
          </div>
        )}

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
            disabled={isLoading || !!error || allCandles.length === 0}
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
            ⟳ Refresh
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
            {displaySymbol}
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
            <p>
              {mode === MODE_HISTORICAL
                ? <>Loading <strong>{activeEvent.title}</strong>…</>
                : <>Loading market data for <strong>{symbol}</strong>…</>
              }
            </p>
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
            symbol={displaySymbol}
          />
        )}

        {!isLoading && !error && allCandles.length === 0 && (
          <div className="practice-status">
            <p>No candles loaded. Press ▶ Play to start.</p>
          </div>
        )}
      </div>

      {/* ── Info footer ── */}
      <div className="practice-info">
        {mode === MODE_LIVE ? (
          <>
            <div className="practice-info__card">
              <h4>Live Data Mode</h4>
              <ul>
                <li>Real 5-minute OHLCV candles from NSE via Yahoo Finance (last 5 trading days).</li>
                <li>Candles reveal one-by-one at your chosen speed — like watching a live market.</li>
                <li>Press <strong>Pause</strong> at any time to study the price structure.</li>
                <li>No real money — ideal for pattern recognition before live play.</li>
              </ul>
            </div>
            <div className="practice-info__card">
              <h4>Tips</h4>
              <ul>
                <li>Use <strong>1×</strong> speed to feel the natural market rhythm.</li>
                <li>Look for support / resistance levels as candles form.</li>
                <li>Try to call the next move before resuming — then check yourself.</li>
                <li>Switch to <strong>Simulator Mode</strong> to test strategies with paper orders.</li>
              </ul>
            </div>
          </>
        ) : (
          <>
            <div className="practice-info__card">
              <h4>Historical Events Mode</h4>
              <ul>
                <li>Real OHLCV data from iconic NSE crashes and rallies.</li>
                <li>Daily candles for older events, hourly for recent ones.</li>
                <li>Pause and study how price behaved at key support / resistance zones.</li>
                <li>Each event has a linked stock most impacted by that macro event.</li>
              </ul>
            </div>
            <div className="practice-info__card">
              <h4>Learning Objectives</h4>
              <ul>
                <li><strong>Crashes</strong> — identify capitulation candles and reversal signals.</li>
                <li><strong>Rallies</strong> — spot accumulation bases and breakout entries.</li>
                <li>Compare different events to build a mental model of market cycles.</li>
                <li>Apply what you learn in <strong>Simulator Mode</strong> with paper trades.</li>
              </ul>
            </div>
          </>
        )}
      </div>

    </div>
  );
}
