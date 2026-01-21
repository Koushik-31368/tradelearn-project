// frontend/src/data/strategies.js

export const strategyCatalog = [

/* ============================================================
   STRATEGY 1: SMA CROSSOVER
============================================================ */
{
  slug: 'sma-cross',
  name: 'SMA Crossover',
  kind: 'SMA_CROSS',
  tagline: 'Catch the wave when momentum shifts',
  hero: 'Picture two lines racing on a chart: when the fast sprinter overtakes the slow walker, momentum is turning bullish—hop in! When the sprinter tires and drops back, exit before the slide.',

  concept: {
    simple: 'This strategy buys when a short-term average (fast SMA) crosses above a long-term average (slow SMA), signaling that recent prices are rising faster than the historical trend. It sells when the fast SMA crosses back below.',
    why: 'Trends persist. Once a stock builds momentum, it often continues for a while. The crossover detects that early momentum flip without trying to catch exact tops or bottoms.'
  },

  rules: {
    entry: 'Go long when SMA(fast) crosses above SMA(slow) from below.',
    exit: 'Close position when SMA(fast) crosses below SMA(slow).',
    position: 'All-in on entry; flat on exit.'
  },

  defaults: { initialCapital: 100000, smaFast: 5, smaSlow: 10 }
},

/* ============================================================
   STRATEGY 2: RSI MEAN REVERSION
============================================================ */
{
  slug: 'rsi-reversion',
  name: 'RSI Mean Reversion',
  kind: 'RSI',
  comingSoon: true,
  tagline: 'Buy the dip, sell the rip',
  hero: 'Imagine a rubber band: stretch it too far and it snaps back. RSI finds those extremes.',

  concept: {
    simple: 'RSI measures overbought and oversold conditions on a 0–100 scale.',
    why: 'Prices tend to revert after extreme moves.'
  },

  rules: {
    entry: 'Buy when RSI drops below 30.',
    exit: 'Sell when RSI rises above 70.',
    position: 'All-in on entry; flat on exit.'
  },

  defaults: { initialCapital: 100000, rsiPeriod: 14 }
},

/* ============================================================
   STRATEGY 3: BOLLINGER BAND BOUNCE
============================================================ */
{
  slug: 'bollinger-bounce',
  name: 'Bollinger Band Bounce',
  kind: 'BOLLINGER',
  comingSoon: true,
  tagline: 'Buy low, sell high inside the bands',
  hero: 'Price behaves like a ball inside a tunnel—touch the bottom, it bounces up.',

  concept: {
    simple: 'Bollinger Bands show statistically extreme prices.',
    why: 'Extreme deviations usually revert to the mean.'
  },

  rules: {
    entry: 'Buy at lower band.',
    exit: 'Sell at upper band.',
    position: 'All-in on entry; flat on exit.'
  },

  defaults: { initialCapital: 100000, period: 20, stdDev: 2 }
},

/* ============================================================
   STRATEGY 4: MACD MOMENTUM
============================================================ */
{
  slug: 'macd-momentum',
  name: 'MACD Momentum',
  kind: 'MACD',
  comingSoon: true,
  tagline: 'Trade momentum shifts',
  hero: 'MACD acts like a momentum speedometer—when it flips, momentum follows.',

  concept: {
    simple: 'MACD tracks momentum changes using EMAs.',
    why: 'Momentum shifts before price trends become obvious.'
  },

  rules: {
    entry: 'Buy when MACD crosses above signal.',
    exit: 'Sell when MACD crosses below signal.',
    position: 'All-in on entry; flat on exit.'
  },

  defaults: { initialCapital: 100000, fast: 12, slow: 26, signal: 9 }
},

/* ============================================================
   STRATEGY 5: 52-WEEK BREAKOUT (FIXED LINE HERE ✅)
============================================================ */
{
  slug: 'breakout-52week',
  name: '52-Week Breakout',
  kind: 'BREAKOUT',
  comingSoon: true,
  tagline: 'Ride the rocket when price breaks highs',
  hero: 'When a stock hits a new 52-week high, every buyer from the past year is in profit—no one is underwater trying to "get out even." That\'s fuel for explosive continuation.',

  concept: {
    simple: 'Stocks breaking yearly highs face no overhead resistance.',
    why: 'Momentum attracts more momentum.'
  },

  rules: {
    entry: 'Buy when price closes above 52-week high.',
    exit: 'Sell on trend weakness.',
    position: 'All-in on entry; flat on exit.'
  },

  defaults: { initialCapital: 100000, lookback: 252 }
},

/* ============================================================
   STRATEGY 6: SUPPORT / RESISTANCE
============================================================ */
{
  slug: 'support-resistance',
  name: 'Support Resistance Bounce',
  kind: 'SUPPORT_RESISTANCE',
  comingSoon: true,
  tagline: 'Buy the floor, sell the ceiling',
  hero: 'Markets remember levels. Trade those memories.',

  concept: {
    simple: 'Support and resistance act as psychological price zones.',
    why: 'Traders cluster orders at known levels.'
  },

  rules: {
    entry: 'Buy at support.',
    exit: 'Sell at resistance.',
    position: 'All-in on entry; flat on exit.'
  },

  defaults: { initialCapital: 100000 }
},

/* ============================================================
   STRATEGY 7: MA RIBBON
============================================================ */
{
  slug: 'ma-ribbon',
  name: 'Moving Average Ribbon',
  kind: 'MA_RIBBON',
  comingSoon: true,
  tagline: 'Trend strength visualized',
  hero: 'When all averages align, trends are powerful.',

  concept: {
    simple: 'Multiple MAs confirm trend direction.',
    why: 'Alignment across timeframes reduces noise.'
  },

  rules: {
    entry: 'Buy when ribbon stacks bullish.',
    exit: 'Sell when ribbon breaks.',
    position: 'All-in on entry; flat on exit.'
  },

  defaults: { initialCapital: 100000 }
},

/* ============================================================
   STRATEGY 8: GAP FILL REVERSAL
============================================================ */
{
  slug: 'gap-fill',
  name: 'Gap Fill Reversal',
  kind: 'GAP_FILL',
  comingSoon: true,
  tagline: 'Fade panic gaps',
  hero: 'Markets overreact—gaps tend to close.',

  concept: {
    simple: 'Most gaps retrace within days.',
    why: 'Emotional selling creates imbalance.'
  },

  rules: {
    entry: 'Buy after large gap down.',
    exit: 'Sell after gap fill.',
    position: 'All-in on entry; flat on exit.'
  },

  defaults: { initialCapital: 100000 }
}

];
