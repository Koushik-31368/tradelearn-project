import React, { useState } from 'react';
import './CandleDiagram.css';

const CANDLE_PRESETS = {
  bullish: { open: 120, close: 155, high: 168, low: 112, label: 'Bullish Candle', color: '#00ff88' },
  bearish: { open: 155, close: 120, high: 168, low: 112, label: 'Bearish Candle', color: '#ff4d4f' },
  doji:    { open: 140, close: 141, high: 165, low: 115, label: 'Doji', color: '#8b949e' },
  hammer:  { open: 150, close: 155, high: 160, low: 115, label: 'Hammer', color: '#00ff88' },
};

const CandleDiagram = () => {
  const [activePreset, setActivePreset] = useState('bullish');
  const candle = CANDLE_PRESETS[activePreset];

  const isBullish = candle.close >= candle.open;
  const bodyTop = Math.max(candle.open, candle.close);
  const bodyBottom = Math.min(candle.open, candle.close);

  // Map price to SVG Y (inverted: higher price = lower Y)
  const chartHeight = 200;
  const padding = 20;
  const range = candle.high - candle.low || 1;
  const priceToY = (price) =>
    padding + ((candle.high - price) / range) * (chartHeight - 2 * padding);

  const wickX = 100;
  const bodyWidth = 40;
  const bodyHeight = Math.max(priceToY(bodyBottom) - priceToY(bodyTop), 3);

  return (
    <div className="candle-diagram">
      <div className="candle-diagram__header">
        <span className="candle-diagram__badge">Interactive</span>
        <h4 className="candle-diagram__title">OHLC Candlestick Anatomy</h4>
      </div>

      <div className="candle-diagram__tabs">
        {Object.entries(CANDLE_PRESETS).map(([key, preset]) => (
          <button
            key={key}
            className={`candle-tab${activePreset === key ? ' candle-tab--active' : ''}`}
            onClick={() => setActivePreset(key)}
          >
            {preset.label}
          </button>
        ))}
      </div>

      <div className="candle-diagram__chart">
        <svg viewBox="0 0 200 200" className="candle-svg">
          {/* Upper wick */}
          <line
            x1={wickX} y1={priceToY(candle.high)}
            x2={wickX} y2={priceToY(bodyTop)}
            stroke={candle.color} strokeWidth="2"
          />
          {/* Lower wick */}
          <line
            x1={wickX} y1={priceToY(bodyBottom)}
            x2={wickX} y2={priceToY(candle.low)}
            stroke={candle.color} strokeWidth="2"
          />
          {/* Body */}
          <rect
            x={wickX - bodyWidth / 2}
            y={priceToY(bodyTop)}
            width={bodyWidth}
            height={bodyHeight}
            fill={isBullish ? candle.color : candle.color}
            fillOpacity={isBullish ? 0.25 : 0.85}
            stroke={candle.color}
            strokeWidth="1.5"
            rx="3"
          />

          {/* Labels */}
          <text x="155" y={priceToY(candle.high) + 4} className="candle-label">
            High: {candle.high}
          </text>
          <text x="155" y={priceToY(candle.low) + 4} className="candle-label">
            Low: {candle.low}
          </text>
          <text x="10" y={priceToY(candle.open) + 4} className="candle-label candle-label--left">
            O: {candle.open}
          </text>
          <text x="10" y={priceToY(candle.close) + 4} className="candle-label candle-label--left">
            C: {candle.close}
          </text>

          {/* Dashed lines */}
          <line
            x1={wickX - bodyWidth / 2 - 5} y1={priceToY(candle.high)}
            x2="152" y2={priceToY(candle.high)}
            stroke="#30363d" strokeWidth="0.5" strokeDasharray="3,3"
          />
          <line
            x1={wickX - bodyWidth / 2 - 5} y1={priceToY(candle.low)}
            x2="152" y2={priceToY(candle.low)}
            stroke="#30363d" strokeWidth="0.5" strokeDasharray="3,3"
          />
        </svg>
      </div>

      <div className="candle-diagram__legend">
        <div className="candle-legend-item">
          <span className="candle-legend-dot" style={{ background: '#00ff88' }} />
          <span>Bullish (Close &gt; Open)</span>
        </div>
        <div className="candle-legend-item">
          <span className="candle-legend-dot" style={{ background: '#ff4d4f' }} />
          <span>Bearish (Close &lt; Open)</span>
        </div>
        <div className="candle-legend-item">
          <span className="candle-legend-dot" style={{ background: '#8b949e' }} />
          <span>Doji (Open ≈ Close)</span>
        </div>
      </div>
    </div>
  );
};

export default CandleDiagram;
