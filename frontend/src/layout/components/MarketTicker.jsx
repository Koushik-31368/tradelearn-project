import React from 'react';
import './MarketTicker.css';

const QUOTES = [
  ['TLX', '1,842.60', '+2.84%', 'up'],
  ['CNDL', '428.15', '+1.26%', 'up'],
  ['RISK', '96.40', '-0.74%', 'down'],
  ['ELO', '2,104', '+38', 'up'],
  ['PIT', '68.5%', 'WIN RATE', 'up'],
  ['OPEN', '09:30', 'MARKET LIVE', 'live'],
];

const MarketTicker = () => (
  <div className="market-ticker" aria-label="TradeLearn market ticker">
    <div className="market-ticker__label"><span className="market-ticker__dot" /> LIVE PIT</div>
    <div className="market-ticker__viewport">
      <div className="market-ticker__track">
        {[...QUOTES, ...QUOTES].map(([symbol, price, change, tone], index) => (
          <span className="market-ticker__quote" key={`${symbol}-${index}`}>
            <b>{symbol}</b><strong>{price}</strong><em className={`market-ticker__change market-ticker__change--${tone}`}>{change}</em>
          </span>
        ))}
      </div>
    </div>
  </div>
);

export default MarketTicker;
