// src/components/simulator/PortfolioSummary.jsx
import React, { useMemo } from 'react';
import './PortfolioSummary.css';

// Tiny inline sparkline using SVG
const Sparkline = ({ data, color = '#00ff88', width = 120, height = 40 }) => {
  if (!data || data.length < 2) return null;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const step = width / (data.length - 1);

  const points = data
    .map((v, i) => `${i * step},${height - ((v - min) / range) * (height - 4) - 2}`)
    .join(' ');

  return (
    <svg width={width} height={height} className="sparkline-svg">
      <defs>
        <linearGradient id={`spark-grad-${color.replace('#', '')}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.3" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polyline
        fill="none"
        stroke={color}
        strokeWidth="1.5"
        points={points}
      />
      <polygon
        fill={`url(#spark-grad-${color.replace('#', '')})`}
        points={`0,${height} ${points} ${width},${height}`}
      />
    </svg>
  );
};

const PortfolioSummary = ({ portfolio, stocks, equityCurve }) => {
  const stats = useMemo(() => {
    const holdingsValue = Object.entries(portfolio.holdings).reduce((sum, [sym, h]) => {
      const stock = stocks.find((s) => s.symbol === sym);
      if (!stock) return sum;
      return sum + h.qty * stock.price;
    }, 0);

    const totalValue = portfolio.cash + holdingsValue;
    const initialValue = 1000000;
    const todayPnL = totalValue - initialValue;
    const todayPnLPercent = ((todayPnL / initialValue) * 100).toFixed(2);

    return { totalValue, holdingsValue, todayPnL, todayPnLPercent };
  }, [portfolio, stocks]);

  const sparkData = useMemo(() => {
    if (!equityCurve || equityCurve.length === 0) return [];
    return equityCurve.map((p) => p.value);
  }, [equityCurve]);

  const isProfit = stats.todayPnL >= 0;

  return (
    <div className="portfolio-summary">
      {/* Portfolio Value */}
      <div className="summary-card summary-card--primary">
        <div className="summary-card__header">
          <span className="summary-card__label">Portfolio Value</span>
          <span className="summary-card__badge">LIVE</span>
        </div>
        <div className="summary-card__value">
          ₹{stats.totalValue.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </div>
        <div className="summary-card__sparkline">
          <Sparkline data={sparkData} color="#00ff88" width={160} height={36} />
        </div>
      </div>

      {/* Today's P/L */}
      <div className={`summary-card ${isProfit ? 'summary-card--profit' : 'summary-card--loss'}`}>
        <div className="summary-card__header">
          <span className="summary-card__label">Today's P&L</span>
          <span className={`summary-card__indicator ${isProfit ? 'indicator--up' : 'indicator--down'}`}>
            {isProfit ? '▲' : '▼'}
          </span>
        </div>
        <div className={`summary-card__value ${isProfit ? 'text-profit' : 'text-loss'}`}>
          {isProfit ? '+' : ''}₹{stats.todayPnL.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </div>
        <div className={`summary-card__sub ${isProfit ? 'text-profit' : 'text-loss'}`}>
          {isProfit ? '+' : ''}{stats.todayPnLPercent}%
        </div>
      </div>

      {/* Available Cash */}
      <div className="summary-card">
        <div className="summary-card__header">
          <span className="summary-card__label">Available Cash</span>
          <svg className="summary-card__icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#8b949e" strokeWidth="2">
            <rect x="2" y="6" width="20" height="12" rx="2" />
            <path d="M2 10h20" />
          </svg>
        </div>
        <div className="summary-card__value">
          ₹{portfolio.cash.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </div>
        <div className="summary-card__sub text-muted">
          Buying Power
        </div>
      </div>

      {/* Holdings Value */}
      <div className="summary-card">
        <div className="summary-card__header">
          <span className="summary-card__label">Holdings Value</span>
          <svg className="summary-card__icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#8b949e" strokeWidth="2">
            <path d="M3 3v18h18" />
            <path d="M7 16l4-4 4 4 5-5" />
          </svg>
        </div>
        <div className="summary-card__value">
          ₹{stats.holdingsValue.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </div>
        <div className="summary-card__sub text-muted">
          {Object.keys(portfolio.holdings).length} position{Object.keys(portfolio.holdings).length !== 1 ? 's' : ''}
        </div>
      </div>
    </div>
  );
};

export default PortfolioSummary;
