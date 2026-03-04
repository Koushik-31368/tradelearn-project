// src/components/simulator/CandlestickChart.jsx
// Pure SVG candlestick chart with volume bars, SMA toggle, and timeframe selector.
import React, { useMemo, useState } from 'react';
import './CandlestickChart.css';

const TIMEFRAMES = [
  { label: '1D', days: 1 },
  { label: '5D', days: 5 },
  { label: '1M', days: 30 },
];

const CandlestickChart = ({ candles = [], smaData = [], symbol }) => {
  const [timeframe, setTimeframe] = useState('1M');
  const [showSMA, setShowSMA] = useState(false);

  const tf = TIMEFRAMES.find((t) => t.label === timeframe) || TIMEFRAMES[2];
  const visibleCandles = candles.slice(-tf.days - 1);
  const visibleSMA = smaData.slice(-tf.days - 1);

  const CHART_W = 700;
  const CHART_H = 320;
  const VOL_H = 60;
  const PADDING = { top: 20, right: 60, bottom: 4, left: 10 };

  const chartData = useMemo(() => {
    if (visibleCandles.length === 0) return null;

    const priceMin = Math.min(...visibleCandles.map((c) => c.low)) * 0.998;
    const priceMax = Math.max(...visibleCandles.map((c) => c.high)) * 1.002;
    const volMax = Math.max(...visibleCandles.map((c) => c.volume), 1);
    const priceRange = priceMax - priceMin || 1;
    const usableW = CHART_W - PADDING.left - PADDING.right;
    const usableH = CHART_H - PADDING.top - PADDING.bottom - VOL_H - 8;
    const candleW = Math.min(usableW / visibleCandles.length, 24);
    const gap = (usableW - candleW * visibleCandles.length) / (visibleCandles.length + 1);

    const items = visibleCandles.map((c, i) => {
      const x = PADDING.left + gap + i * (candleW + gap);
      const isGreen = c.close >= c.open;
      const bodyTop = PADDING.top + ((priceMax - Math.max(c.open, c.close)) / priceRange) * usableH;
      const bodyBot = PADDING.top + ((priceMax - Math.min(c.open, c.close)) / priceRange) * usableH;
      const wickTop = PADDING.top + ((priceMax - c.high) / priceRange) * usableH;
      const wickBot = PADDING.top + ((priceMax - c.low) / priceRange) * usableH;
      const volH = (c.volume / volMax) * VOL_H;
      const volY = CHART_H - volH;

      return {
        ...c,
        x,
        candleW,
        isGreen,
        bodyTop,
        bodyBot,
        wickTop,
        wickBot,
        volH,
        volY,
      };
    });

    // SMA line points
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

    // Price grid lines
    const gridLines = [];
    const gridCount = 5;
    for (let i = 0; i <= gridCount; i++) {
      const price = priceMin + (priceRange * i) / gridCount;
      const y = PADDING.top + usableH - (usableH * i) / gridCount;
      gridLines.push({ price, y });
    }

    return { items, smaPoints, gridLines, priceMin, priceMax, usableH };
  }, [visibleCandles, visibleSMA, showSMA]);

  if (!candles || candles.length === 0) {
    return (
      <div className="candlestick-chart">
        <div className="candlestick-chart__empty">Select a stock to view chart</div>
      </div>
    );
  }

  const lastCandle = visibleCandles[visibleCandles.length - 1];
  const prevCandle = visibleCandles.length > 1 ? visibleCandles[visibleCandles.length - 2] : lastCandle;
  const dayChange = lastCandle.close - prevCandle.close;
  const dayChangePct = ((dayChange / prevCandle.close) * 100).toFixed(2);
  const isUp = dayChange >= 0;

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
            {isUp ? '+' : ''}{dayChange.toFixed(2)} ({isUp ? '+' : ''}{dayChangePct}%)
          </span>
        </div>
        <div className="candlestick-chart__controls">
          <div className="candlestick-chart__timeframes">
            {TIMEFRAMES.map((t) => (
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
          >
            SMA
          </button>
        </div>
      </div>

      {/* Chart */}
      <div className="candlestick-chart__canvas">
        <svg viewBox={`0 0 ${CHART_W} ${CHART_H}`} preserveAspectRatio="none" className="candlestick-chart__svg">
          {/* Grid */}
          {chartData?.gridLines.map((g, i) => (
            <g key={i}>
              <line
                x1={PADDING.left}
                y1={g.y}
                x2={CHART_W - PADDING.right}
                y2={g.y}
                stroke="#21262d"
                strokeWidth="0.5"
              />
              <text
                x={CHART_W - PADDING.right + 6}
                y={g.y + 4}
                fill="#484f58"
                fontSize="8"
                fontFamily="monospace"
              >
                {g.price.toFixed(0)}
              </text>
            </g>
          ))}

          {/* Candles */}
          {chartData?.items.map((c, i) => (
            <g key={i} className="candlestick-chart__candle-group">
              {/* Wick */}
              <line
                x1={c.x + c.candleW / 2}
                y1={c.wickTop}
                x2={c.x + c.candleW / 2}
                y2={c.wickBot}
                stroke={c.isGreen ? '#00ff88' : '#ff4d4f'}
                strokeWidth="1"
              />
              {/* Body */}
              <rect
                x={c.x}
                y={c.bodyTop}
                width={c.candleW}
                height={Math.max(c.bodyBot - c.bodyTop, 1)}
                fill={c.isGreen ? '#00ff88' : '#ff4d4f'}
                rx="1"
                opacity="0.85"
              />
              {/* Volume */}
              <rect
                x={c.x}
                y={c.volY}
                width={c.candleW}
                height={c.volH}
                fill={c.isGreen ? 'rgba(0,255,136,0.15)' : 'rgba(255,77,79,0.15)'}
                rx="1"
              />
            </g>
          ))}

          {/* SMA Line */}
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

      {/* OHLCV Ticker */}
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
