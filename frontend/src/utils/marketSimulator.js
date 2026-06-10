/**
 * src/utils/marketSimulator.js
 *
 * Simulated Market Engine for TradeLearn Simulator.
 *
 * Generates realistic OHLCV candlestick data with the following phases:
 *   UPTREND      - Sustained upward price movement
 *   DOWNTREND    - Sustained downward price movement
 *   SIDEWAYS     - Horizontal consolidation within a range
 *   BREAKOUT     - Explosive directional move after consolidation
 *   PULLBACK     - Counter-trend retracement within a larger trend
 *
 * Phases transition randomly every 20–40 candles.
 * Occasional pattern injections: bullish engulfing, double top, breakout expansion.
 */

// ─── Phase Constants ─────────────────────────────────────────────────────────

export const PHASES = {
  UPTREND: 'UPTREND',
  DOWNTREND: 'DOWNTREND',
  SIDEWAYS: 'SIDEWAYS',
  BREAKOUT: 'BREAKOUT',
  PULLBACK: 'PULLBACK',
};

/**
 * Phase configuration:
 *   drift      – per-candle mean price change (as a fraction of price)
 *   volatility – standard deviation of the per-candle change
 *   wickBias   – ratio of wick size relative to body; higher = longer wicks
 *   volMult    – volume multiplier vs base volume
 */
const PHASE_CONFIG = {
  [PHASES.UPTREND]:   { drift:  0.0028, volatility: 0.0070, wickBias: 0.35, volMult: 1.2 },
  [PHASES.DOWNTREND]: { drift: -0.0028, volatility: 0.0070, wickBias: 0.35, volMult: 1.2 },
  [PHASES.SIDEWAYS]:  { drift:  0.0002, volatility: 0.0035, wickBias: 0.70, volMult: 0.8 },
  [PHASES.BREAKOUT]:  { drift:  0.0090, volatility: 0.0130, wickBias: 0.15, volMult: 2.5 },
  [PHASES.PULLBACK]:  { drift: -0.0045, volatility: 0.0100, wickBias: 0.50, volMult: 1.0 },
};

/**
 * Legal next phases for each phase.
 * e.g. after UPTREND we can only go into PULLBACK, SIDEWAYS, or DOWNTREND.
 */
const PHASE_TRANSITIONS = {
  [PHASES.UPTREND]:   [PHASES.PULLBACK,  PHASES.SIDEWAYS,  PHASES.DOWNTREND],
  [PHASES.DOWNTREND]: [PHASES.SIDEWAYS,  PHASES.BREAKOUT,  PHASES.UPTREND],
  [PHASES.SIDEWAYS]:  [PHASES.BREAKOUT,  PHASES.UPTREND,   PHASES.DOWNTREND],
  [PHASES.BREAKOUT]:  [PHASES.UPTREND,   PHASES.PULLBACK],
  [PHASES.PULLBACK]:  [PHASES.UPTREND,   PHASES.SIDEWAYS],
};

// ─── Box-Muller normal random ─────────────────────────────────────────────────

function gaussRandom(mean = 0, sd = 1) {
  let u = 0, v = 0;
  while (u === 0) u = Math.random();
  while (v === 0) v = Math.random();
  return mean + sd * Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * v);
}

function randInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randFloat(min, max) {
  return min + Math.random() * (max - min);
}

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function clamp(val, min, max) {
  return Math.min(Math.max(val, min), max);
}

// ─── Candle Generator ─────────────────────────────────────────────────────────

/**
 * Generates a single candle given the previous close price and the current phase.
 * Includes realistic upper/lower wicks and volume.
 *
 * @param {number} prevClose  - The previous candle's closing price
 * @param {string} phase      - One of PHASES.*
 * @param {number} baseVolume - Base volume units (default 1,000,000)
 * @returns {{ open, high, low, close, volume, time }}
 */
function generateCandle(prevClose, phase, baseVolume = 1_000_000, timestamp = Date.now()) {
  const cfg = PHASE_CONFIG[phase];

  const open = prevClose;

  // Normally-distributed price change centred on the phase drift
  const changePct = gaussRandom(cfg.drift, cfg.volatility);
  // Clamp extreme moves to ±4 % per candle
  const clampedPct = clamp(changePct, -0.04, 0.04);
  const close = +(open * (1 + clampedPct)).toFixed(2);

  const bodyHigh = Math.max(open, close);
  const bodyLow  = Math.min(open, close);
  const bodySize = Math.abs(open - close);

  // Upper and lower wicks — proportional to body size + a small absolute component
  const absWick    = open * 0.002;
  const upperWick  = bodySize * cfg.wickBias * randFloat(0.3, 1.2) + absWick * Math.random();
  const lowerWick  = bodySize * cfg.wickBias * randFloat(0.3, 1.2) + absWick * Math.random();

  const high = +(bodyHigh + upperWick).toFixed(2);
  const low  = +(bodyLow  - lowerWick).toFixed(2);

  // Volume: normally-distributed around phase multiplier × baseVolume
  const volume = Math.max(
    10_000,
    Math.round(gaussRandom(cfg.volMult * baseVolume, cfg.volMult * baseVolume * 0.3))
  );

  return {
    open: +open.toFixed(2),
    high,
    low,
    close,
    volume,
    time: timestamp,
    phase,
  };
}

// ─── Pattern Injectors ────────────────────────────────────────────────────────

/**
 * Bullish Engulfing: Large green candle that fully covers the previous red candle.
 * prev candle should be bearish.
 */
function patternBullishEngulfing(prevCandle, timestamp) {
  const prevBodySize = Math.abs(prevCandle.open - prevCandle.close);
  const open  = prevCandle.close - prevBodySize * randFloat(0.05, 0.20);
  const close = prevCandle.open  + prevBodySize * randFloat(0.10, 0.40);
  const high  = +(close + prevBodySize * randFloat(0.05, 0.15)).toFixed(2);
  const low   = +(open  - prevBodySize * randFloat(0.05, 0.15)).toFixed(2);
  return {
    open:   +open.toFixed(2),
    high,
    low,
    close:  +close.toFixed(2),
    volume: Math.round(prevCandle.volume * randFloat(1.5, 2.5)),
    time:   timestamp,
    phase:  PHASES.UPTREND,
    pattern: 'bullish_engulfing',
  };
}

/**
 * Breakout Candle: Strong directional move with above-average volume & tiny wick.
 */
function patternBreakout(prevClose, direction, timestamp) {
  const magnitude = randFloat(0.018, 0.035);  // 1.8 – 3.5 % surge
  const open  = prevClose;
  const close = +(prevClose * (1 + direction * magnitude)).toFixed(2);
  const bodyHigh = Math.max(open, close);
  const bodyLow  = Math.min(open, close);
  const tinyWick = Math.abs(open - close) * 0.05;
  return {
    open:   +open.toFixed(2),
    high:   +(bodyHigh + tinyWick).toFixed(2),
    low:    +(bodyLow  - tinyWick).toFixed(2),
    close,
    volume: Math.round(randFloat(2_000_000, 5_000_000)),
    time:   timestamp,
    phase:  direction > 0 ? PHASES.BREAKOUT : PHASES.DOWNTREND,
    pattern: 'breakout',
  };
}

/**
 * Double-Top second peak: Candle that tags a previous high then reverses.
 */
function patternDoubleTop(prevClose, resistancePrice, timestamp) {
  const spike = +(resistancePrice * randFloat(1.000, 1.006)).toFixed(2);  // touch resistance
  const open  = prevClose;
  const close = +(prevClose * randFloat(0.990, 0.998)).toFixed(2);        // close lower → reversal
  return {
    open,
    high:   spike,
    low:    +(close - Math.abs(open - close) * randFloat(0.1, 0.3)).toFixed(2),
    close,
    volume: Math.round(randFloat(800_000, 1_600_000)),
    time:   timestamp,
    phase:  PHASES.DOWNTREND,
    pattern: 'double_top',
  };
}

// ─── Market Simulator Factory ─────────────────────────────────────────────────

/**
 * Creates a stateful market simulator.
 *
 * @param {number} basePrice  - Starting price (default 1400)
 * @returns MarketSimulator object
 *
 * Usage:
 *   const sim = createMarketSimulator(1450);
 *   const candle = sim.nextCandle();
 *   const state  = sim.getState(); // { phase, candleCount, phaseRemaining, currentPrice }
 *   sim.reset();
 */
export function createMarketSimulator(basePrice = 1400) {
  // ── Internal state ───────────────────────────────────────────────────────
  let currentPrice    = basePrice;
  let currentPhase    = PHASES.UPTREND;
  let candlesInPhase  = 0;            // how many candles generated in current phase
  let phaseDuration   = randInt(20, 40); // total candles this phase will last
  let totalCandleCount = 0;
  let lastCandle      = null;

  // Resistance level tracking for double-top detection
  let recentHighs     = [];
  let patternCooldown = 0;            // candles to wait before next pattern injection

  // ── Phase transition ─────────────────────────────────────────────────────
  function advancePhase() {
    const next = pick(PHASE_TRANSITIONS[currentPhase]);
    currentPhase   = next;
    phaseDuration  = randInt(20, 40);
    candlesInPhase = 0;
  }

  // ── Pattern injection decision ────────────────────────────────────────────
  function shouldInjectPattern() {
    if (patternCooldown > 0) { patternCooldown--; return null; }
    const roll = Math.random();

    // 3 % chance of bullish engulfing after a bearish candle
    if (roll < 0.03 && lastCandle && lastCandle.close < lastCandle.open) {
      patternCooldown = 8;
      return 'bullish_engulfing';
    }
    // 2 % chance of breakout candle while in SIDEWAYS
    if (roll < 0.05 && currentPhase === PHASES.SIDEWAYS) {
      patternCooldown = 15;
      return 'breakout';
    }
    // 2 % chance of double top near a recent high in a declining UPTREND
    if (roll < 0.07 && recentHighs.length >= 5) {
      const maxHigh  = Math.max(...recentHighs);
      const proximity = Math.abs(currentPrice - maxHigh) / maxHigh;
      if (proximity < 0.015) {
        patternCooldown = 12;
        return 'double_top';
      }
    }
    return null;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Generate the next candle and advance internal state.
   * @param {number} [timestamp] - Unix ms timestamp. Defaults to Date.now().
   * @returns Candle object { open, high, low, close, volume, time, phase, pattern? }
   */
  function nextCandle(timestamp = Date.now()) {
    // Check phase transition
    if (candlesInPhase >= phaseDuration) {
      advancePhase();
    }

    let candle;
    const pattern = shouldInjectPattern();

    if (pattern === 'bullish_engulfing' && lastCandle) {
      candle = patternBullishEngulfing(lastCandle, timestamp);
    } else if (pattern === 'breakout') {
      // Breakout direction follows recent trend
      const direction = currentPrice > basePrice ? 1 : -1;
      candle = patternBreakout(currentPrice, direction, timestamp);
      advancePhase(); // breakout triggers phase change
    } else if (pattern === 'double_top') {
      const resistance = Math.max(...recentHighs);
      candle = patternDoubleTop(currentPrice, resistance, timestamp);
    } else {
      candle = generateCandle(currentPrice, currentPhase, 1_000_000, timestamp);
    }

    // Update state
    currentPrice   = candle.close;
    lastCandle     = candle;
    candlesInPhase++;
    totalCandleCount++;

    // Track recent highs (last 20)
    recentHighs.push(candle.high);
    if (recentHighs.length > 20) recentHighs.shift();

    return candle;
  }

  /** Returns a snapshot of the current simulator state (read-only). */
  function getState() {
    return {
      phase:            currentPhase,
      candlesInPhase,
      phaseRemaining:   phaseDuration - candlesInPhase,
      phaseDuration,
      totalCandleCount,
      currentPrice:     +currentPrice.toFixed(2),
    };
  }

  /** Reset to initial conditions with an optional new base price. */
  function reset(newBasePrice = basePrice) {
    currentPrice      = newBasePrice;
    currentPhase      = PHASES.UPTREND;
    candlesInPhase    = 0;
    phaseDuration     = randInt(20, 40);
    totalCandleCount  = 0;
    lastCandle        = null;
    recentHighs       = [];
    patternCooldown   = 0;
  }

  return { nextCandle, getState, reset };
}

// ─── Initial Historical Data Generator ───────────────────────────────────────

/**
 * Generates an initial dataset of `count` historical candles using the market
 * simulator. Timestamps are spaced 2 seconds apart, ending at `now`.
 *
 * @param {number} basePrice  - Starting price
 * @param {number} count      - Number of candles to generate (default 100)
 * @returns {Array} Array of candle objects
 */
export function generateInitialCandles(basePrice = 1400, count = 100) {
  const sim = createMarketSimulator(basePrice);
  const candles = [];
  const now = Date.now();
  // Walk backwards: oldest first
  const startTime = now - count * 2000;

  for (let i = 0; i < count; i++) {
    const timestamp = startTime + i * 2000;
    candles.push(sim.nextCandle(timestamp));
  }

  return candles;
}

/**
 * Compute a Simple Moving Average over candle close prices.
 * Returns an array of the same length; entries before period-1 are null.
 *
 * @param {Array}  candles
 * @param {number} period
 * @returns {Array<number|null>}
 */
export function computeLiveSMA(candles, period = 14) {
  return candles.map((_, i) => {
    if (i < period - 1) return null;
    let sum = 0;
    for (let j = i - period + 1; j <= i; j++) sum += candles[j].close;
    return +(sum / period).toFixed(2);
  });
}

/**
 * Human-readable label for each phase.
 */
export const PHASE_LABELS = {
  [PHASES.UPTREND]:   '↑ Uptrend',
  [PHASES.DOWNTREND]: '↓ Downtrend',
  [PHASES.SIDEWAYS]:  '→ Consolidation',
  [PHASES.BREAKOUT]:  '⚡ Breakout',
  [PHASES.PULLBACK]:  '↩ Pullback',
};

/**
 * CSS class suffix for each phase (used by chart badge).
 */
export const PHASE_CLASSES = {
  [PHASES.UPTREND]:   'up',
  [PHASES.DOWNTREND]: 'down',
  [PHASES.SIDEWAYS]:  'sideways',
  [PHASES.BREAKOUT]:  'breakout',
  [PHASES.PULLBACK]:  'pullback',
};
