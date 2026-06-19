// src/components/simulator/MarketSentiment.jsx
// Compact sentiment panel — derives trend, volatility, and momentum from candle data.
// All calculations are self-contained; no backend dependency.
import React, { useMemo } from 'react';
import './MarketSentiment.css';

/* ── Calculation helpers ───────────────────────────────── */

/**
 * Trend: ratio of green candles in the last 10.
 * >60 % green → Bullish, >60 % red → Bearish, else Neutral.
 */
function computeTrend(candles) {
  const recent = candles.slice(-10);
  if (recent.length === 0) return 'Neutral';
  const green = recent.filter((c) => c.close >= c.open).length;
  const ratio = green / recent.length;
  if (ratio > 0.6) return 'Bullish';
  if (ratio < 0.4) return 'Bearish';
  return 'Neutral';
}

/**
 * Volatility: avg range of last 10 vs avg range of last 50.
 * > 1.25× baseline → High, < 0.75× → Low, else Moderate.
 */
function computeVolatility(candles) {
  const avgRange = (slice) =>
    slice.length === 0
      ? 0
      : slice.reduce((s, c) => s + (c.high - c.low), 0) / slice.length;

  const recent = candles.slice(-10);
  const baseline = candles.slice(-50);
  if (recent.length === 0 || baseline.length === 0) return 'Moderate';

  const recentAvg = avgRange(recent);
  const baselineAvg = avgRange(baseline);
  if (baselineAvg === 0) return 'Moderate';

  const ratio = recentAvg / baselineAvg;
  if (ratio > 1.25) return 'High';
  if (ratio < 0.75) return 'Low';
  return 'Moderate';
}

/**
 * Momentum: close of latest candle vs close 10 candles ago.
 * > +2 % → Increasing, < −2 % → Decreasing, else Sideways.
 */
function computeMomentum(candles) {
  if (candles.length < 11) return 'Sideways';
  const latest = candles[candles.length - 1].close;
  const earlier = candles[candles.length - 11].close;
  if (earlier === 0) return 'Sideways';
  const pctChange = (latest - earlier) / earlier;
  if (pctChange > 0.02) return 'Increasing';
  if (pctChange < -0.02) return 'Decreasing';
  return 'Sideways';
}

/* ── Colour helpers ────────────────────────────────────── */
const VALUE_COLOR = {
  Bullish: '#00ff88',
  Bearish: '#ff4d4f',
  Increasing: '#00ff88',
  Decreasing: '#ff4d4f',
  High: '#ff4d4f',
};

function valueColor(val) {
  return VALUE_COLOR[val] || '#ffffff';
}

/* ── Component ─────────────────────────────────────────── */
const MarketSentiment = ({ candles = [] }) => {
  const trend = useMemo(() => computeTrend(candles), [candles]);
  const volatility = useMemo(() => computeVolatility(candles), [candles]);
  const momentum = useMemo(() => computeMomentum(candles), [candles]);

  return (
    <div className="ms-panel">
      <h3 className="ms-title">Market Sentiment</h3>
      <div className="ms-rows">
        <div className="ms-row">
          <span className="ms-label">Trend</span>
          <span className="ms-value" style={{ color: valueColor(trend) }}>
            {trend}
          </span>
        </div>
        <div className="ms-row">
          <span className="ms-label">Volatility</span>
          <span className="ms-value" style={{ color: valueColor(volatility) }}>
            {volatility}
          </span>
        </div>
        <div className="ms-row">
          <span className="ms-label">Momentum</span>
          <span className="ms-value" style={{ color: valueColor(momentum) }}>
            {momentum}
          </span>
        </div>
      </div>
    </div>
  );
};

export default MarketSentiment;
