// src/utils/strategyDetector.js
// Detects common candlestick patterns in a candle array and returns
// an educational hint object, or null if no pattern is detected.

/**
 * @param {Array<{open: number, high: number, low: number, close: number}>} candles
 * @returns {{ type: string, message: string, direction: 'bullish'|'bearish'|'neutral' } | null}
 */
export function detectStrategies(candles) {
  if (candles.length < 3) return null;

  const last = candles[candles.length - 1];
  const prev = candles[candles.length - 2];

  // ── Bullish Engulfing ───────────────────────────────────────────────────
  // Previous candle is bearish (red), current is bullish (green) and
  // fully engulfs the previous body.
  if (
    prev.close < prev.open &&
    last.close > last.open &&
    last.open < prev.close &&
    last.close > prev.open
  ) {
    return {
      type: 'bullish_engulfing',
      direction: 'bullish',
      message:
        '🟢 Bullish Engulfing detected — a green candle has fully covered the previous red candle, signalling a possible upward reversal.',
    };
  }

  // ── Bearish Engulfing ───────────────────────────────────────────────────
  // Previous candle is bullish (green), current is bearish (red) and
  // fully engulfs the previous body.
  if (
    prev.close > prev.open &&
    last.close < last.open &&
    last.open > prev.close &&
    last.close < prev.open
  ) {
    return {
      type: 'bearish_engulfing',
      direction: 'bearish',
      message:
        '🔴 Bearish Engulfing detected — a red candle has fully covered the previous green candle, signalling a possible downward reversal.',
    };
  }

  // ── Hammer ──────────────────────────────────────────────────────────────
  // Small body in upper half, long lower shadow (≥ 2× body), little upper shadow.
  {
    const body = Math.abs(last.close - last.open);
    const lowerShadow = Math.min(last.close, last.open) - last.low;
    const upperShadow = last.high - Math.max(last.close, last.open);
    if (body > 0 && lowerShadow >= 2 * body && upperShadow <= 0.3 * body) {
      return {
        type: 'hammer',
        direction: 'bullish',
        message:
          '🔨 Hammer pattern detected — a long lower wick with a small body suggests sellers were overpowered; potential bullish reversal ahead.',
      };
    }
  }

  // ── Shooting Star ───────────────────────────────────────────────────────
  // Small body in lower half, long upper shadow (≥ 2× body), little lower shadow.
  {
    const body = Math.abs(last.close - last.open);
    const upperShadow = last.high - Math.max(last.close, last.open);
    const lowerShadow = Math.min(last.close, last.open) - last.low;
    if (body > 0 && upperShadow >= 2 * body && lowerShadow <= 0.3 * body) {
      return {
        type: 'shooting_star',
        direction: 'bearish',
        message:
          '⭐ Shooting Star detected — a long upper wick shows buyers failed to hold gains; potential bearish reversal ahead.',
      };
    }
  }

  // ── Breakout above 20-candle high ───────────────────────────────────────
  if (candles.length >= 20) {
    const window = candles.slice(-20, -1); // exclude current candle
    const maxHigh = Math.max(...window.map((c) => c.high));
    if (last.close > maxHigh) {
      return {
        type: 'breakout',
        direction: 'bullish',
        message:
          '🚀 Breakout detected — price closed above the 20-candle resistance high, suggesting momentum is building.',
      };
    }
  }

  // ── Breakdown below 20-candle low ───────────────────────────────────────
  if (candles.length >= 20) {
    const window = candles.slice(-20, -1);
    const minLow = Math.min(...window.map((c) => c.low));
    if (last.close < minLow) {
      return {
        type: 'breakdown',
        direction: 'bearish',
        message:
          '📉 Breakdown detected — price closed below the 20-candle support low, suggesting increased selling pressure.',
      };
    }
  }

  return null;
}

/**
 * Returns the "textbook correct" response for a given strategy type.
 * Used for post-decision feedback.
 * @param {string} strategyType
 * @returns {'buy'|'sell'|'hold'}
 */
export function expectedDecision(strategyType) {
  const bullish = ['bullish_engulfing', 'hammer', 'breakout'];
  const bearish = ['bearish_engulfing', 'shooting_star', 'breakdown'];
  if (bullish.includes(strategyType)) return 'buy';
  if (bearish.includes(strategyType)) return 'sell';
  return 'hold';
}
