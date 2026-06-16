// src/components/simulator/CandlestickChart.jsx
// Candlestick chart for Historical Market Replay.
import React, { useMemo, useState } from 'react';
import './CandlestickChart.css';

// ── Static (historical) timeframes ──────────────────────────────────────────
const STATIC_TIMEFRAMES = [
  { label: '1D',  count: 1  },
  { label: '5D',  count: 5  },
  { label: '1M',  count: 30 },
  { label: '3M',  count: 90 },
];

// ── SVG layout constants ──────────────────────────────────────────────────────
const CHART_W  = 700;
const CHART_H  = 320;
const VOL_H    = 60;
const PADDING  = { top: 20, right: 66, bottom: 4, left: 10 };
const GRID_CNT = 5;

/**
 * CandlestickChart
 * Props:
 *   candles    {Array}   – static candle data from Historical Replay
 *   smaData    {Array}   – pre-computed SMA
 *   symbol     {string}  – ticker displayed in the header
 */
const CandlestickChart = ({
  candles = [],
  smaData = [],
  symbol,
}) => {
  const [showSMA,      setShowSMA]      = useState(false);
  const [timeframe,    setTimeframe]    = useState('1M');

  // Select active data source + timeframe list
  const activeTF       = STATIC_TIMEFRAMES.find((t) => t.label === timeframe) || STATIC_TIMEFRAMES[2];
  const visibleCandles = candles.slice(-activeTF.count);

  const visibleSMA = smaData.slice(-activeTF.count);

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
  if (candles.length === 0) {
    return (
      <div className="candlestick-chart">
        <div className="candlestick-chart__empty">
          {'Select a stock to view chart'}
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
        </div>

        <div className="candlestick-chart__controls">
          <div className="candlestick-chart__timeframes">
            {STATIC_TIMEFRAMES.map((t) => (
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
      </div>
    </div>
  );
};

export default CandlestickChart;
