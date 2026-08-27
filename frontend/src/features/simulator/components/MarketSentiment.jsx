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

/**
 * Simple RSI-like score (0–100) from last 14 candles.
 * Standard RSI: avg gain / (avg gain + avg loss) × 100
 */
function computeRSI(candles, period = 14) {
  const slice = candles.slice(-period - 1);
  if (slice.length < 2) return 50;
  let gains = 0, losses = 0;
  for (let i = 1; i < slice.length; i++) {
    const delta = slice[i].close - slice[i - 1].close;
    if (delta > 0) gains += delta;
    else losses += Math.abs(delta);
  }
  const n = slice.length - 1;
  const avgGain = gains / n;
  const avgLoss = losses / n;
  if (avgGain + avgLoss === 0) return 50;
  return Math.round((avgGain / (avgGain + avgLoss)) * 100);
}

/** Compute a 0–100 gauge score for each metric */
function gaugeScore(metric, value) {
  if (metric === 'trend') {
    if (value === 'Bullish') return 80;
    if (value === 'Bearish') return 20;
    return 50;
  }
  if (metric === 'volatility') {
    if (value === 'High') return 85;
    if (value === 'Low') return 15;
    return 50;
  }
  if (metric === 'momentum') {
    if (value === 'Increasing') return 78;
    if (value === 'Decreasing') return 22;
    return 50;
  }
  return 50;
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
  const trend      = useMemo(() => computeTrend(candles),      [candles]);
  const volatility = useMemo(() => computeVolatility(candles), [candles]);
  const momentum   = useMemo(() => computeMomentum(candles),   [candles]);
  const rsi        = useMemo(() => computeRSI(candles),        [candles]);

  const rows = [
    { label: 'Trend',      value: trend,      metric: 'trend' },
    { label: 'Volatility', value: volatility,  metric: 'volatility' },
    { label: 'Momentum',   value: momentum,    metric: 'momentum' },
  ];

  const rsiColor = rsi >= 70 ? '#ff4d4f' : rsi <= 30 ? '#00ff88' : '#ffffff';

  return (
    <div className="ms-panel">
      <h3 className="ms-title">Market Sentiment</h3>
      <div className="ms-rows">
        {rows.map(({ label, value, metric }) => {
          const score = gaugeScore(metric, value);
          const color = valueColor(value);
          return (
            <div className="ms-row" key={label}>
              <div className="ms-row-top">
                <span className="ms-label">{label}</span>
                <span className="ms-value" style={{ color }}>{value}</span>
              </div>
              <div className="ms-gauge-track" aria-hidden="true">
                <div
                  className="ms-gauge-fill"
                  style={{ width: `${score}%`, background: color }}
                />
              </div>
            </div>
          );
        })}

        {/* RSI Row */}
        <div className="ms-row">
          <div className="ms-row-top">
            <span className="ms-label">RSI (14)</span>
            <span className="ms-value" style={{ color: rsiColor }}>{rsi}</span>
          </div>
          <div className="ms-gauge-track" aria-hidden="true">
            <div
              className="ms-gauge-fill"
              style={{ width: `${rsi}%`, background: rsiColor }}
            />
          </div>
        </div>
      </div>
    </div>
  );
};

export default MarketSentiment;
