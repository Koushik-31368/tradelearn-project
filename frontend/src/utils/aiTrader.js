// src/utils/aiTrader.js
// Simple rule-based AI trader that analyses recent candlestick data
// and returns a trading decision: 'buy', 'sell', or 'hold'.

/**
 * Decide what the AI would do given a candle history.
 *
 * Strategy:
 *  - Uses a 5-candle Simple Moving Average (SMA) compared to the last close.
 *  - Additionally checks RSI-lite (average gain vs loss over 14 candles)
 *    to avoid chasing overbought / oversold conditions.
 *
 * @param {Array<{open: number, high: number, low: number, close: number}>} candles
 * @returns {'buy' | 'sell' | 'hold'}
 */
export function aiDecision(candles) {
  if (candles.length < 10) return 'hold';

  const last = candles[candles.length - 1];

  // ── 5-candle SMA ────────────────────────────────────────────────────────
  const recent5 = candles.slice(-5);
  const sma5 = recent5.reduce((sum, c) => sum + c.close, 0) / recent5.length;

  // ── 14-candle RSI-lite ──────────────────────────────────────────────────
  const rsiPeriod = Math.min(14, candles.length - 1);
  let gains = 0;
  let losses = 0;
  for (let i = candles.length - rsiPeriod; i < candles.length; i++) {
    const change = candles[i].close - candles[i - 1].close;
    if (change > 0) gains += change;
    else losses += Math.abs(change);
  }
  const avgGain = gains / rsiPeriod;
  const avgLoss = losses / rsiPeriod;
  const rs = avgLoss === 0 ? 100 : avgGain / avgLoss;
  const rsi = 100 - 100 / (1 + rs);

  // ── Decision logic ──────────────────────────────────────────────────────
  // Buy when price is above the SMA (momentum up) AND not overbought (RSI < 70).
  if (last.close > sma5 && rsi < 70) return 'buy';

  // Sell when price is below the SMA (momentum down) AND not oversold (RSI > 30).
  if (last.close < sma5 && rsi > 30) return 'sell';

  // Otherwise stay flat.
  return 'hold';
}

/**
 * Returns a human-readable label for an AI decision.
 * @param {'buy'|'sell'|'hold'} decision
 * @returns string
 */
export function aiDecisionLabel(decision) {
  switch (decision) {
    case 'buy':  return '📈 Buy';
    case 'sell': return '📉 Sell';
    default:     return '⏸ Hold';
  }
}
