// src/components/StockTicker.jsx
import React from 'react';
import './StockTicker.css';

const StockTicker = () => {
  // We list the items twice to create a seamless loop
  const tickerItems = [
    { symbol: 'AAPL', change: '+2.4%' },
    { symbol: 'TSLA', change: '+5.1%' },
    { symbol: 'GOOGL', change: '+1.8%' },
    { symbol: 'AMZN', change: '-0.5%' },
    { symbol: 'MSFT', change: '+1.2%' },
    { symbol: 'NVDA', change: '+4.7%' },
    { symbol: 'AAPL', change: '+2.4%' },
    { symbol: 'TSLA', change: '+5.1%' },
    { symbol: 'GOOGL', change: '+1.8%' },
    { symbol: 'AMZN', change: '-0.5%' },
    { symbol: 'MSFT', change: '+1.2%' },
    { symbol: 'NVDA', change: '+4.7%' },
  ];

  return (
    <div className="ticker-wrap">
      <div className="ticker-move">
        {tickerItems.map((item, index) => (
          <div className="ticker-item" key={index}>
            {item.symbol} <span className={item.change.startsWith('+') ? 'positive' : 'negative'}>{item.change}</span>
          </div>
        ))}
      </div>
    </div>
  );
};

export default StockTicker;