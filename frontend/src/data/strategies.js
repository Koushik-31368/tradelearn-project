// src/data/strategies.js
export const strategyCatalog = [
  {
    slug: 'sma-cross',
    name: 'SMA Crossover',
    kind: 'SMA_CROSS',
    summary: 'Buy when fast SMA crosses above slow SMA; sell on opposite.',
    rules: {
      entry: 'Go long when SMA(fast) crosses above SMA(slow).',
      exit: 'Close when SMA(fast) crosses below SMA(slow).'
    },
    defaults: { initialCapital: 100000, smaFast: 5, smaSlow: 10 },
    params: [
      { key: 'smaFast', label: 'Fast SMA', type: 'number', min: 1 },
      { key: 'smaSlow', label: 'Slow SMA', type: 'number', min: 2 }
    ],
    example: {
      text: 'If fast=5 and slow=10, when 5‑SMA moves above 10‑SMA on 2024‑01‑12, buy; when it moves back below on 2024‑01‑23, sell.',
    }
  },
  {
    slug: 'rsi-reversion',
    name: 'RSI Reversion',
    kind: 'RSI', // backend not yet implemented
    comingSoon: true,
    summary: 'Buy oversold (RSI < 30), sell overbought (RSI > 70).',
    rules: { entry: 'Buy when RSI < lower.', exit: 'Sell when RSI > upper.' },
    defaults: { initialCapital: 100000, rsiPeriod: 14, lower: 30, upper: 70 },
    params: [
      { key: 'rsiPeriod', label: 'RSI Period', type: 'number', min: 2 },
      { key: 'lower', label: 'Lower (Buy <)', type: 'number', min: 1, max: 50 },
      { key: 'upper', label: 'Upper (Sell >)', type: 'number', min: 50, max: 99 }
    ],
    example: { text: 'Buy when RSI dips to 28; sell when it rises past 70.' }
  }
];