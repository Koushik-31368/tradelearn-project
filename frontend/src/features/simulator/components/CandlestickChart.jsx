// src/features/simulator/components/CandlestickChart.jsx
import React, { useMemo, useState, useCallback } from 'react';
import './CandlestickChart.css';

// ── SVG layout constants ───────────────────────────────────────────────────
const W       = 800;
const H       = 340;
const VOL_H   = 56;
const PAD     = { top: 24, right: 72, bottom: 4, left: 12 };
const GRID    = 5;
const USABLE_W = W - PAD.left - PAD.right;
const USABLE_H = H - PAD.top  - PAD.bottom - VOL_H - 10;

// ── Timeframe config ───────────────────────────────────────────────────────
const TIMEFRAMES = [
  { label: '1W', count: 5  },
  { label: '1M', count: 22 },
  { label: '3M', count: 66 },
  { label: 'All', count: Infinity },
];

// ── Loading skeleton ───────────────────────────────────────────────────────
function ChartSkeleton() {
  return (
    <div className="cc-skeleton">
      <div className="cc-skeleton__header">
        <div className="cc-skeleton__pill cc-skeleton__pill--wide" />
        <div className="cc-skeleton__pill" />
      </div>
      <div className="cc-skeleton__bars">
        {Array.from({ length: 18 }).map((_, i) => (
          <div
            key={i}
            className="cc-skeleton__bar"
            style={{ height: `${30 + Math.sin(i * 0.7) * 25 + 20}px`, animationDelay: `${i * 60}ms` }}
          />
        ))}
      </div>
    </div>
  );
}

// ── Main component ─────────────────────────────────────────────────────────
const CandlestickChart = ({ candles = [], smaData = [], symbol, isLoading = false }) => {
  const [showSMA,  setShowSMA]  = useState(true);
  const [tfLabel,  setTfLabel]  = useState('1M');
  const [hoverIdx, setHoverIdx] = useState(null);

  const tf             = TIMEFRAMES.find((t) => t.label === tfLabel) || TIMEFRAMES[1];
  const visibleCandles = candles.slice(-Math.min(tf.count, candles.length));
  const visibleSMA     = smaData.slice(-Math.min(tf.count, smaData.length));

  const chartData = useMemo(() => {
    if (visibleCandles.length === 0) return null;

    const priceMin   = Math.min(...visibleCandles.map((c) => c.low))  * 0.997;
    const priceMax   = Math.max(...visibleCandles.map((c) => c.high)) * 1.003;
    const volMax     = Math.max(...visibleCandles.map((c) => c.volume), 1);
    const priceRange = priceMax - priceMin || 1;
    const n          = visibleCandles.length;
    const candleW    = Math.min(USABLE_W / n, 20);
    const gap        = (USABLE_W - candleW * n) / (n + 1);

    const items = visibleCandles.map((c, i) => {
      const x       = PAD.left + gap + i * (candleW + gap);
      const isGreen = c.close >= c.open;
      const bodyTop = PAD.top + ((priceMax - Math.max(c.open, c.close)) / priceRange) * USABLE_H;
      const bodyBot = PAD.top + ((priceMax - Math.min(c.open, c.close)) / priceRange) * USABLE_H;
      const wickTop = PAD.top + ((priceMax - c.high) / priceRange) * USABLE_H;
      const wickBot = PAD.top + ((priceMax - c.low)  / priceRange) * USABLE_H;
      const volH    = (c.volume / volMax) * VOL_H;
      const volY    = H - volH;
      return { ...c, x, candleW, isGreen, bodyTop, bodyBot, wickTop, wickBot, volH, volY };
    });

    // SMA line
    let smaPoints = '';
    if (showSMA && visibleSMA.length > 0) {
      smaPoints = visibleSMA
        .map((val, i) => {
          if (val == null) return null;
          const x = PAD.left + gap + i * (candleW + gap) + candleW / 2;
          const y = PAD.top + ((priceMax - val) / priceRange) * USABLE_H;
          return `${x},${y}`;
        })
        .filter(Boolean)
        .join(' ');
    }

    // Grid lines
    const gridLines = Array.from({ length: GRID + 1 }, (_, i) => {
      const price = priceMin + (priceRange * i) / GRID;
      const y     = PAD.top + USABLE_H - (USABLE_H * i) / GRID;
      return { price, y };
    });

    return { items, smaPoints, gridLines, priceMin, priceMax, priceRange };
  }, [visibleCandles, visibleSMA, showSMA]);

  const handleMouseMove = useCallback((e) => {
    if (!chartData) return;
    const svg   = e.currentTarget.getBoundingClientRect();
    const mouseX = ((e.clientX - svg.left) / svg.width) * W;
    let   closest = null, minDist = Infinity;
    chartData.items.forEach((c, i) => {
      const cx = c.x + c.candleW / 2;
      const d  = Math.abs(mouseX - cx);
      if (d < minDist) { minDist = d; closest = i; }
    });
    setHoverIdx(closest);
  }, [chartData]);

  // ── Guards ────────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div className="candlestick-chart">
        <ChartSkeleton />
      </div>
    );
  }

  if (candles.length === 0) {
    return (
      <div className="candlestick-chart">
        <div className="candlestick-chart__empty">
          <span className="cc-empty-icon">📊</span>
          <span>Select a stock from the watchlist to load its chart</span>
        </div>
      </div>
    );
  }

  const lastCandle    = visibleCandles[visibleCandles.length - 1];
  const prevCandle    = visibleCandles.length > 1 ? visibleCandles[visibleCandles.length - 2] : lastCandle;
  const dayChange     = lastCandle.close - prevCandle.close;
  const dayChangePct  = ((dayChange / prevCandle.close) * 100).toFixed(2);
  const isUp          = dayChange >= 0;
  const hoverCandle   = hoverIdx !== null ? chartData?.items[hoverIdx] : null;

  return (
    <div className="candlestick-chart">

      {/* Header */}
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
            {TIMEFRAMES.map((t) => (
              <button
                key={t.label}
                className={`candlestick-chart__tf-btn${tfLabel === t.label ? ' candlestick-chart__tf-btn--active' : ''}`}
                onClick={() => setTfLabel(t.label)}
              >
                {t.label}
              </button>
            ))}
          </div>
          <button
            className={`candlestick-chart__sma-btn${showSMA ? ' candlestick-chart__sma-btn--active' : ''}`}
            onClick={() => setShowSMA((v) => !v)}
          >
            SMA 7
          </button>
        </div>
      </div>

      {/* Crosshair tooltip */}
      {hoverCandle && (
        <div className="candlestick-chart__tooltip">
          <span className="cc-tt-date">{hoverCandle.date}</span>
          <span>O <b>{hoverCandle.open.toFixed(2)}</b></span>
          <span>H <b className="cc-tt-high">{hoverCandle.high.toFixed(2)}</b></span>
          <span>L <b className="cc-tt-low">{hoverCandle.low.toFixed(2)}</b></span>
          <span>C <b style={{ color: hoverCandle.isGreen ? 'var(--green-bright)' : 'var(--danger)' }}>{hoverCandle.close.toFixed(2)}</b></span>
          <span>V <b>{(hoverCandle.volume / 1e6).toFixed(2)}M</b></span>
        </div>
      )}

      {/* SVG chart */}
      <div className="candlestick-chart__canvas">
        <svg
          viewBox={`0 0 ${W} ${H}`}
          preserveAspectRatio="xMidYMid meet"
          className="candlestick-chart__svg"
          onMouseMove={handleMouseMove}
          onMouseLeave={() => setHoverIdx(null)}
        >
          {/* Grid lines — very subtle, dark */}
          {chartData?.gridLines.map((g, i) => (
            <g key={i}>
              <line
                x1={PAD.left} y1={g.y}
                x2={W - PAD.right} y2={g.y}
                stroke="#22263a"
                strokeWidth="1"
                strokeDasharray={i === 0 ? '0' : '4,6'}
              />
              <text
                x={W - PAD.right + 6} y={g.y + 4}
                fill="#5C5C74"
                fontSize="9"
                fontFamily="IBM Plex Mono, monospace"
                fontWeight="500"
              >
                {g.price.toFixed(0)}
              </text>
            </g>
          ))}

          {/* Volume bars — subtle */}
          {chartData?.items.map((c, i) => (
            <rect
              key={`vol-${i}`}
              x={c.x} y={c.volY}
              width={c.candleW} height={c.volH}
              fill={c.isGreen ? 'rgba(57,255,136,0.13)' : 'rgba(255,59,92,0.13)'}
              rx="1"
            />
          ))}

          {/* Candles */}
          {chartData?.items.map((c, i) => (
            <g key={i}>
              {/* Hover crosshair */}
              {i === hoverIdx && (
                <line
                  x1={c.x + c.candleW / 2} y1={PAD.top}
                  x2={c.x + c.candleW / 2} y2={H - VOL_H - 2}
                  stroke="rgba(255,212,0,0.25)"
                  strokeWidth="1"
                  strokeDasharray="3,5"
                />
              )}
              {/* Wick */}
              <line
                x1={c.x + c.candleW / 2} y1={c.wickTop}
                x2={c.x + c.candleW / 2} y2={c.wickBot}
                stroke={c.isGreen ? '#39FF88' : '#FF3B5C'}
                strokeWidth="1.2"
              />
              {/* Body */}
              <rect
                x={c.x} y={c.bodyTop}
                width={c.candleW}
                height={Math.max(c.bodyBot - c.bodyTop, 1.5)}
                fill={c.isGreen ? '#39FF88' : '#FF3B5C'}
                rx="1.5"
                opacity={i === hoverIdx ? 1 : 0.88}
              />
            </g>
          ))}

          {/* SMA polyline */}
          {showSMA && chartData?.smaPoints && (
            <polyline
              points={chartData.smaPoints}
              fill="none"
              stroke="var(--gold)"
              strokeWidth="1.5"
              strokeLinejoin="round"
              opacity="0.85"
            />
          )}
        </svg>
      </div>

      {/* OHLCV footer */}
      <div className="candlestick-chart__ohlcv">
        <span><b>O</b> {lastCandle.open.toFixed(2)}</span>
        <span><b>H</b> {lastCandle.high.toFixed(2)}</span>
        <span><b>L</b> {lastCandle.low.toFixed(2)}</span>
        <span><b>C</b> {lastCandle.close.toFixed(2)}</span>
        <span><b>V</b> {(lastCandle.volume / 1e6).toFixed(2)}M</span>
        {showSMA && visibleSMA[visibleSMA.length - 1] != null && (
          <span className="cc-sma-label"><b>SMA7</b> {visibleSMA[visibleSMA.length - 1]?.toFixed(2)}</span>
        )}
      </div>
    </div>
  );
};

export default CandlestickChart;
