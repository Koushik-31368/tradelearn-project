// src/components/simulator/PerformanceChart.jsx
// Equity curve area chart drawn with pure SVG.
import React, { useMemo } from 'react';
import './PerformanceChart.css';

const PerformanceChart = ({ data = [] }) => {
  const W = 800;
  const H = 200;
  const PAD = { top: 16, right: 50, bottom: 28, left: 10 };

  const chart = useMemo(() => {
    if (data.length < 2) return null;

    const values = data.map((d) => d.value);
    const min = Math.min(...values) * 0.998;
    const max = Math.max(...values) * 1.002;
    const range = max - min || 1;

    const usableW = W - PAD.left - PAD.right;
    const usableH = H - PAD.top - PAD.bottom;
    const step = usableW / (data.length - 1);

    const points = data.map((d, i) => {
      const x = PAD.left + i * step;
      const y = PAD.top + usableH - ((d.value - min) / range) * usableH;
      return { x, y, ...d };
    });

    const lineStr = points.map((p) => `${p.x},${p.y}`).join(' ');
    const areaStr = `${PAD.left},${PAD.top + usableH} ${lineStr} ${points[points.length - 1].x},${PAD.top + usableH}`;

    const isProfit = values[values.length - 1] >= values[0];
    const color = isProfit ? '#00ff88' : '#ff4d4f';

    // Grid
    const gridLines = [];
    for (let i = 0; i <= 4; i++) {
      const val = min + (range * i) / 4;
      const y = PAD.top + usableH - (usableH * i) / 4;
      gridLines.push({ val, y });
    }

    // Date labels (show every ~5th)
    const dateLabels = data
      .filter((_, i) => i % Math.ceil(data.length / 7) === 0 || i === data.length - 1)
      .map((d, i, arr) => {
        const idx = data.indexOf(d);
        return { label: d.date, x: PAD.left + idx * step };
      });

    return { points, lineStr, areaStr, color, gridLines, dateLabels, isProfit, min, max, range };
  }, [data]);

  if (!chart) {
    return (
      <div className="performance-chart">
        <div className="performance-chart__header">
          <h3 className="performance-chart__title">Portfolio Performance</h3>
        </div>
        <div className="performance-chart__empty">No performance data yet</div>
      </div>
    );
  }

  const startVal = data[0].value;
  const endVal = data[data.length - 1].value;
  const totalReturn = ((endVal - startVal) / startVal * 100).toFixed(2);

  return (
    <div className="performance-chart">
      <div className="performance-chart__header">
        <div>
          <h3 className="performance-chart__title">Portfolio Performance</h3>
          <span className="performance-chart__subtitle">30-Day Equity Curve</span>
        </div>
        <div className="performance-chart__stats">
          <span className={`performance-chart__return ${chart.isProfit ? 'up' : 'down'}`}>
            {chart.isProfit ? '+' : ''}{totalReturn}%
          </span>
          <span className="performance-chart__period">30D Return</span>
        </div>
      </div>

      <div className="performance-chart__canvas">
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="performance-chart__svg">
          <defs>
            <linearGradient id="perf-gradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={chart.color} stopOpacity="0.2" />
              <stop offset="100%" stopColor={chart.color} stopOpacity="0" />
            </linearGradient>
          </defs>

          {/* Grid */}
          {chart.gridLines.map((g, i) => (
            <g key={i}>
              <line
                x1={PAD.left}
                y1={g.y}
                x2={W - PAD.right}
                y2={g.y}
                stroke="#21262d"
                strokeWidth="0.5"
              />
              <text x={W - PAD.right + 6} y={g.y + 3} fill="#484f58" fontSize="7" fontFamily="monospace">
                {(g.val / 1e5).toFixed(1)}L
              </text>
            </g>
          ))}

          {/* Area fill */}
          <polygon points={chart.areaStr} fill="url(#perf-gradient)" />

          {/* Line */}
          <polyline
            points={chart.lineStr}
            fill="none"
            stroke={chart.color}
            strokeWidth="2"
            strokeLinejoin="round"
          />

          {/* Date labels */}
          {chart.dateLabels.map((d, i) => (
            <text key={i} x={d.x} y={H - 6} fill="#484f58" fontSize="7" fontFamily="monospace" textAnchor="middle">
              {d.label}
            </text>
          ))}

          {/* End dot */}
          <circle
            cx={chart.points[chart.points.length - 1].x}
            cy={chart.points[chart.points.length - 1].y}
            r="3"
            fill={chart.color}
            stroke="#161b22"
            strokeWidth="2"
          />
        </svg>
      </div>
    </div>
  );
};

export default PerformanceChart;
