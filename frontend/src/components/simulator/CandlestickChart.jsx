// src/components/simulator/CandlestickChart.jsx
// Candlestick chart with optional live market simulation engine.
import React, { useMemo, useState, useEffect, useRef, useCallback } from 'react';
import {
  createMarketSimulator,
  generateInitialCandles,
  computeLiveSMA,
  PHASE_LABELS,
  PHASE_CLASSES,
} from '../../utils/marketSimulator';
import './CandlestickChart.css';

// ── Static (historical) timeframes ──────────────────────────────────────────
const STATIC_TIMEFRAMES = [
  { label: '1D',  count: 1  },
  { label: '5D',  count: 5  },
  { label: '1M',  count: 30 },
];

// ── Live-mode timeframes (last N candles) ────────────────────────────────────
const LIVE_TIMEFRAMES = [
  { label: '30',   count: 30  },
  { label: '60',   count: 60  },
  { label: '100',  count: 100 },
];

const MAX_CANDLES = 300; // sliding-window cap
const TICK_MS     = 2000; // new candle every 2 seconds

// ── SVG layout constants ──────────────────────────────────────────────────────
const CHART_W  = 700;
const CHART_H  = 320;
const VOL_H    = 60;
const PADDING  = { top: 20, right: 66, bottom: 4, left: 10 };
const GRID_CNT = 5;

/**
 * CandlestickChart
 * Props:
 *   candles    {Array}   – static candle data (used when liveMode=false)
 *   smaData    {Array}   – pre-computed SMA for static mode
 *   symbol     {string}  – ticker displayed in the header
 *   basePrice  {number}  – starting price for live simulation (default 1400)
 *   liveMode   {boolean} – when true, runs the market simulator (default true)
 */
const CandlestickChart = ({
  candles: staticCandles = [],
  smaData: staticSMA = [],
  symbol,
  basePrice = 1400,
  liveMode = true,
}) => {
  const simulatorRef  = useRef(null);
  const [liveCandles,  setLiveCandles]  = useState([]);
  const [isRunning,    setIsRunning]    = useState(true);
  const [marketPhase,  setMarketPhase]  = useState('UPTREND');
  const [showSMA,      setShowSMA]      = useState(false);
  const [timeframe,    setTimeframe]    = useState(liveMode ? '60' : '1M');
  const [lastPattern,  setLastPattern]  = useState(null);

  // Initialise simulator + seed 100 historical candles on mount / symbol change
  useEffect(() => {
    if (!liveMode) return;
    const startPrice = basePrice && basePrice > 0 ? basePrice : 1400;
    simulatorRef.current = createMarketSimulator(startPrice);
    setLiveCandles(generateInitialCandles(startPrice, 100));
    setMarketPhase(simulatorRef.current.getState().phase);
    setLastPattern(null);
    setIsRunning(true);
  }, [liveMode, symbol, basePrice]);

  // Append one new candle every TICK_MS while running
  const appendCandle = useCallback(() => {
    if (!simulatorRef.current) return;
    const candle = simulatorRef.current.nextCandle(Date.now());
    const state  = simulatorRef.current.getState();
    setLiveCandles((prev) => {
      const next = prev.length >= MAX_CANDLES
        ? [...prev.slice(1), candle]
        : [...prev, candle];
      return next;
    });
    setMarketPhase(state.phase);
    if (candle.pattern) setLastPattern(candle.pattern);
  }, []);

  useEffect(() => {
    if (!liveMode || !isRunning) return;
    const id = setInterval(appendCandle, TICK_MS);
    return () => clearInterval(id);
  }, [liveMode, isRunning, appendCandle]);

  // Select active data source + timeframe list
  const allCandles     = liveMode ? liveCandles    : staticCandles;
  const timeframes     = liveMode ? LIVE_TIMEFRAMES : STATIC_TIMEFRAMES;
  const activeTF       = timeframes.find((t) => t.label === timeframe) || timeframes[1];
  const visibleCandles = allCandles.slice(-activeTF.count);

  // SMA
  const liveSMA    = useMemo(
    () => (liveMode && allCandles.length > 0 ? computeLiveSMA(allCandles, 14) : []),
    [liveMode, allCandles]
  );
  const smaSeries  = liveMode ? liveSMA : staticSMA;
  const visibleSMA = smaSeries.slice(-activeTF.count);

  // SVG layout — memoised so React only recomputes when visible candles change
  const chartData = useMemo(() => {
    if (visibleCandles.length === 0) return null;

    const priceMin   = Math.min(...visibleCandles.map((c) => c.low))  * 0.998;
    const priceMax   = Math.max(...visibleCandles.map((c) => c.high)) * 1.002;
    const volMax     = Math.max(...visibleCandles.map((c) => c.volume), 1);
    const priceRange = priceMax - priceMin || 1;
    const usableW    = CHART_W - PADDING.left - PADDING.right;
    const usableH    = CHART_H - PADDING.top  - PADDING.bottom - VOL_H - 8;
    const candleW    = Math.min(usableW / visibleCandles.length, 18);
    const gap        = (usableW - candleW * visibleCandles.length) / (visibleCandles.length + 1);

    const items = visibleCandles.map((c, i) => {
      const x       = PADDING.left + gap + i * (candleW + gap);
      const isGreen = c.close >= c.open;
      const bodyTop = PADDING.top + ((priceMax - Math.max(c.open, c.close)) / priceRange) * usableH;
      const bodyBot = PADDING.top + ((priceMax - Math.min(c.open, c.close)) / priceRange) * usableH;
      const wickTop = PADDING.top + ((priceMax - c.high) / priceRange) * usableH;
      const wickBot = PADDING.top + ((priceMax - c.low)  / priceRange) * usableH;
      const volH    = (c.volume / volMax) * VOL_H;
      const volY    = CHART_H - volH;
      return { ...c, x, candleW, isGreen, bodyTop, bodyBot, wickTop, wickBot, volH, volY };
    });

    // SMA polyline
    let smaPoints = '';
    if (showSMA) {
      smaPoints = visibleSMA
        .map((val, i) => {
          if (val == null) return null;
          const x = PADDING.left + gap + i * (candleW + gap) + candleW / 2;
          const y = PADDING.top + ((priceMax - val) / priceRange) * usableH;
          return `${x},${y}`;
        })
        .filter(Boolean)
        .join(' ');
    }

    // Price grid
    const gridLines = [];
    for (let i = 0; i <= GRID_CNT; i++) {
      const price = priceMin + (priceRange * i) / GRID_CNT;
      const y     = PADDING.top + usableH - (usableH * i) / GRID_CNT;
      gridLines.push({ price, y });
    }

    return { items, smaPoints, gridLines };
  }, [visibleCandles, visibleSMA, showSMA]);

  // Empty / loading guard
  if (allCandles.length === 0) {
    return (
      <div className="candlestick-chart">
        <div className="candlestick-chart__empty">
          {liveMode ? 'Initialising market engine…' : 'Select a stock to view chart'}
        </div>
      </div>
    );
  }

  const lastCandle   = visibleCandles[visibleCandles.length - 1];
  const prevCandle   = visibleCandles.length > 1 ? visibleCandles[visibleCandles.length - 2] : lastCandle;
  const dayChange    = lastCandle.close - prevCandle.close;
  const dayChangePct = ((dayChange / prevCandle.close) * 100).toFixed(2);
  const isUp         = dayChange >= 0;

  return (
    <div className="candlestick-chart">
      {/* ── Header ── */}
      <div className="candlestick-chart__header">
        <div className="candlestick-chart__info">
          <span className="candlestick-chart__symbol">{symbol}</span>
          <span className="candlestick-chart__last-price">
            ₹{lastCandle.close.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
          </span>
          <span className={`candlestick-chart__day-change ${isUp ? 'up' : 'down'}`}>
            {isUp ? '+' : ''}{dayChange.toFixed(2)}&nbsp;({isUp ? '+' : ''}{dayChangePct}%)
          </span>
          {liveMode && (
            <span className={`candlestick-chart__phase-badge phase--${PHASE_CLASSES[marketPhase]}`}>
              {PHASE_LABELS[marketPhase]}
            </span>
          )}
          {liveMode && lastPattern && (
            <span className="candlestick-chart__pattern-flash" key={lastPattern}>
              {lastPattern === 'bullish_engulfing' && '🟢 Bullish Engulfing'}
              {lastPattern === 'breakout'          && '⚡ Breakout'}
              {lastPattern === 'double_top'        && '🔴 Double Top'}
            </span>
          )}
        </div>

        <div className="candlestick-chart__controls">
          <div className="candlestick-chart__timeframes">
            {timeframes.map((t) => (
              <button
                key={t.label}
                className={`candlestick-chart__tf-btn ${timeframe === t.label ? 'candlestick-chart__tf-btn--active' : ''}`}
                onClick={() => setTimeframe(t.label)}
              >
                {t.label}
              </button>
            ))}
          </div>
          <button
            className={`candlestick-chart__sma-btn ${showSMA ? 'candlestick-chart__sma-btn--active' : ''}`}
            onClick={() => setShowSMA(!showSMA)}
            title="Toggle SMA-14"
          >
            SMA
          </button>
          {liveMode && (
            <button
              className={`candlestick-chart__live-btn ${isRunning ? 'candlestick-chart__live-btn--running' : ''}`}
              onClick={() => setIsRunning((r) => !r)}
              title={isRunning ? 'Pause simulation' : 'Resume simulation'}
            >
              {isRunning ? '⏸ LIVE' : '▶ PAUSED'}
            </button>
          )}
        </div>
      </div>

      {/* ── SVG Chart ── */}
      <div className="candlestick-chart__canvas">
        <svg
          viewBox={`0 0 ${CHART_W} ${CHART_H}`}
          preserveAspectRatio="none"
          className="candlestick-chart__svg"
        >
          {/* Grid */}
          {chartData?.gridLines.map((g, i) => (
            <g key={i}>
              <line
                x1={PADDING.left} y1={g.y}
                x2={CHART_W - PADDING.right} y2={g.y}
                stroke="#21262d" strokeWidth="0.5"
              />
              <text
                x={CHART_W - PADDING.right + 4} y={g.y + 4}
                fill="#484f58" fontSize="8" fontFamily="monospace"
              >
                {g.price.toFixed(0)}
              </text>
            </g>
          ))}

          {/* Candles — stable integer keys let React diff only the new element */}
          {chartData?.items.map((c, i) => (
            <g key={i} className="candlestick-chart__candle-group">
              <line
                x1={c.x + c.candleW / 2} y1={c.wickTop}
                x2={c.x + c.candleW / 2} y2={c.wickBot}
                stroke={c.isGreen ? '#00ff88' : '#ff4d4f'}
                strokeWidth="1"
              />
              <rect
                x={c.x} y={c.bodyTop}
                width={c.candleW}
                height={Math.max(c.bodyBot - c.bodyTop, 1)}
                fill={c.isGreen ? '#00ff88' : '#ff4d4f'}
                rx="1"
                opacity="0.85"
              />
              <rect
                x={c.x} y={c.volY}
                width={c.candleW} height={c.volH}
                fill={c.isGreen ? 'rgba(0,255,136,0.14)' : 'rgba(255,77,79,0.14)'}
                rx="1"
              />
              {/* Golden highlight ring on pattern candles */}
              {c.pattern && (
                <rect
                  x={c.x - 1} y={c.bodyTop - 1}
                  width={c.candleW + 2}
                  height={Math.max(c.bodyBot - c.bodyTop, 1) + 2}
                  fill="none" stroke="#f0b429" strokeWidth="1.5" rx="2" opacity="0.7"
                />
              )}
            </g>
          ))}

          {/* SMA polyline */}
          {showSMA && chartData?.smaPoints && (
            <polyline
              points={chartData.smaPoints}
              fill="none"
              stroke="#f0b429"
              strokeWidth="1.5"
              strokeLinejoin="round"
              opacity="0.8"
            />
          )}
        </svg>
      </div>

      {/* ── OHLCV ticker ── */}
      <div className="candlestick-chart__ohlcv">
        <span><b>O</b> {lastCandle.open.toFixed(2)}</span>
        <span><b>H</b> {lastCandle.high.toFixed(2)}</span>
        <span><b>L</b> {lastCandle.low.toFixed(2)}</span>
        <span><b>C</b> {lastCandle.close.toFixed(2)}</span>
        <span><b>V</b> {(lastCandle.volume / 1e6).toFixed(2)}M</span>
        {liveMode && (
          <span className="candlestick-chart__ohlcv-count">{allCandles.length} candles</span>
        )}
      </div>
    </div>
  );
};

export default CandlestickChart;
