// src/pages/PracticePage.jsx
import React, { useEffect, useState, useRef, useCallback } from 'react';
import { fetchMarketHistory } from '../services/marketApi';
import CandlestickChart from '../components/simulator/CandlestickChart';
import { historicalEvents, findEvent } from '../data/historicalEvents';
import './PracticePage.css';
import { detectStrategies, expectedDecision } from '../utils/strategyDetector';
import { aiDecision, aiDecisionLabel } from '../utils/aiTrader';
import { useAuth } from '../context/AuthContext';
import { backendUrl } from '../utils/api';

// ── Replay speed options (ms per candle) ────────────────────────────────────
const SPEEDS = [
  { label: '0.5×',  ms: 3000 },
  { label: '1×',    ms: 1500 },
  { label: '2×',    ms:  750 },
  { label: '5×',    ms:  300 },
];

export default function PracticePage() {
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

  // ── Feature: Strategy hints ─────────────────────────────────────────────
  const [hint,             setHint]             = useState(null);
  const [decisionFeedback, setDecisionFeedback] = useState(null);

  // ── Feature: AI Opponent ──────────────────────────────────────────────
  const [aiEnabled,       setAiEnabled]       = useState(false);
  const [aiDecisionState, setAiDecisionState] = useState(null);

  // ── Auth (for ELO submission) ─────────────────────────────────────────
  const { user } = useAuth();

  // ── Derived display ───────────────────────────────────────────────────
  const visibleCandles = allCandles.slice(0, visibleCount);
  const progress       = allCandles.length > 0
    ? Math.round((visibleCount / allCandles.length) * 100)
    : 0;
  const lastCandle     = visibleCandles[visibleCandles.length - 1] || null;
  const displaySymbol  = activeEvent.symbol;

  // ── Load data ─────────────────────────────────────────────────────────
  const loadData = useCallback(async (event) => {
    clearInterval(intervalRef.current);
    setIsPlaying(false);
    setHasStarted(false);
    setVisibleCount(0);
    setAllCandles([]);
    setError(null);
    setIsLoading(true);

    try {
      const data = await fetchMarketHistory(event.symbol, event.start, event.end);

      if (data.length === 0) {
        setError(`No data available for "${event.title}". Yahoo Finance may not have this range.`);
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

  // Reload whenever event changes
  useEffect(() => {
    loadData(activeEvent);
    return () => clearInterval(intervalRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

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

  // ── Reset hints & AI state when new data loads ────────────────────────
  useEffect(() => {
    setHint(null);
    setDecisionFeedback(null);
    setAiDecisionState(null);
  }, [allCandles]);

  // ── Strategy detection + AI decision on each newly revealed candle ────
  useEffect(() => {
    const visible = allCandles.slice(0, visibleCount);
    if (visible.length < 3) return;

    const detected = detectStrategies(visible);
    // Only surface a new hint when the pattern type actually changes
    if (detected && (!hint || hint.type !== detected.type)) {
      setHint(detected);
      setDecisionFeedback(null);
    }

    if (aiEnabled) {
      const move = aiDecision(visible);
      setAiDecisionState(move);
    }
    // visibleCount is the only trigger we need; other deps are stable refs
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visibleCount]);

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

  const handleReload = () => loadData(activeEvent);

  // ── Strategy decision handler ─────────────────────────────────────────
  const handleDecision = async (decision) => {
    if (!hint) return;
    const correct = expectedDecision(hint.type);
    const isRight = decision === correct;
    const aiMove  = aiEnabled ? aiDecisionState : null;

    setDecisionFeedback({ decision, correct, isRight, aiMove });
    setHint(null);

    // Submit ELO delta to practice leaderboard when user is logged in
    if (user?.username) {
      const delta = isRight ? 8 : -4;
      try {
        await fetch(
          backendUrl(`/api/leaderboard/update?username=${encodeURIComponent(user.username)}&scoreDelta=${delta}`),
          { method: 'POST' }
        );
      } catch {
        // Non-critical — silently swallow network errors
      }
    }
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
            <span className="practice-header__badge practice-header__badge--historical">
              HISTORICAL
            </span>
            Practice Mode
          </h1>
          <p className="practice-header__sub">
            Replay iconic market crashes and rallies using real historical data.
          </p>
        </div>
      </div>

      {/* ── Historical event cards ── */}
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

      {/* ── Controls bar ── */}
      <div className="practice-controls">

        {/* Event summary pill */}
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
          <button
            className={`practice-btn practice-btn--ai${aiEnabled ? ' practice-btn--ai-on' : ''}`}
            onClick={() => {
              setAiEnabled((prev) => !prev);
              if (aiEnabled) setAiDecisionState(null);
            }}
          >
            {aiEnabled ? '🤖 AI On' : '🤖 AI Off'}
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
            <p>Loading <strong>{activeEvent.title}</strong>…</p>
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
      </div>

      {/* ── Decision feedback banner ── */}
      {decisionFeedback && (
        <div className={`decision-feedback decision-feedback--${decisionFeedback.isRight ? 'correct' : 'wrong'}`}>
          <span className="decision-feedback__icon">
            {decisionFeedback.isRight ? '✅' : '❌'}
          </span>
          <div className="decision-feedback__text">
            <strong>{decisionFeedback.isRight ? 'Correct!' : 'Not quite.'}</strong>
            {' '}Textbook answer for this pattern:{' '}
            <em className="decision-feedback__answer">{decisionFeedback.correct}</em>.
            {decisionFeedback.aiMove && (
              <span> AI chose: <strong>{aiDecisionLabel(decisionFeedback.aiMove)}</strong>.</span>
            )}
          </div>
          <button className="decision-feedback__close" onClick={() => setDecisionFeedback(null)}>×</button>
        </div>
      )}

      {/* ── Strategy hint panel ── */}
      {hint && (
        <div className={`strategy-hint strategy-hint--${hint.direction}`}>
          <div className="strategy-hint__header">
            <h3 className="strategy-hint__title">📊 Trading Hint</h3>
            <span className="strategy-hint__badge">{hint.type.replace(/_/g, ' ').toUpperCase()}</span>
          </div>
          <p className="strategy-hint__message">{hint.message}</p>
          <p className="strategy-hint__question">What would you do right now?</p>
          <div className="decision-buttons">
            <button className="decision-btn decision-btn--buy"  onClick={() => handleDecision('buy')}>📈 Buy</button>
            <button className="decision-btn decision-btn--sell" onClick={() => handleDecision('sell')}>📉 Sell</button>
            <button className="decision-btn decision-btn--hold" onClick={() => handleDecision('hold')}>⏸ Hold</button>
          </div>
        </div>
      )}

      {/* ── AI Opponent box ── */}
      {aiEnabled && (
        <div className="ai-box">
          <div className="ai-box__header">
            <span className="ai-box__icon">🤖</span>
            <h4 className="ai-box__title">AI Trader</h4>
          </div>
          <p className="ai-box__decision">
            Current call:&nbsp;
            <strong className={`ai-decision ai-decision--${aiDecisionState || 'hold'}`}>
              {aiDecisionLabel(aiDecisionState || 'hold')}
            </strong>
          </p>
          <p className="ai-box__sub">5-candle SMA + RSI-14 momentum strategy.</p>
        </div>
      )}

      {/* ── Info footer ── */}
      <div className="practice-info">
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
      </div>

    </div>
  );
}
