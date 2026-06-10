import React from 'react';
import './StrategyCard.css';

const StrategyCard = ({ strategy, onClick }) => {
  const riskClass =
    strategy.risk === 'LOW' ? 'low' :
    strategy.risk === 'MEDIUM' ? 'medium' : 'high';

  return (
    <button className="strat-card" onClick={() => onClick(strategy)}>
      <div className="strat-card__top">
        <span className="strat-card__icon">{strategy.icon}</span>
        <span className={`strat-card__risk strat-card__risk--${riskClass}`}>
          {strategy.risk}
        </span>
      </div>
      <h3 className="strat-card__name">{strategy.name}</h3>
      <p className="strat-card__desc">{strategy.description}</p>
      <div className="strat-card__meta">
        <span className="strat-card__meta-item">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="7" r="6" stroke="currentColor" strokeWidth="1.2"/><path d="M7 3.5V7L9.5 8.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"/></svg>
          {strategy.timeHorizon}
        </span>
        <span className="strat-card__meta-item">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M2 12L5.5 5L8 8.5L12 2" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round"/></svg>
          {strategy.category}
        </span>
      </div>
      <div className="strat-card__cta">
        View Strategy
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M5 3L9 7L5 11" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>
      </div>
    </button>
  );
};

export default StrategyCard;
