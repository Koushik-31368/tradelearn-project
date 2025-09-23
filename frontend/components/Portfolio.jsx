// src/components/Portfolio.jsx
import React from 'react';
import './Portfolio.css';

// Accept portfolio and cashBalance as props
const Portfolio = ({ portfolio, cashBalance }) => {
  return (
    <div className="portfolio-wrapper">
      <h3>My Portfolio</h3>
      <div className="cash-balance">
        <h4>Cash Balance</h4>
        <p>₹{cashBalance.toLocaleString('en-IN')}</p>
      </div>
      <div className="my-stocks">
        <h4>My Stocks</h4>
        {portfolio.length === 0 ? (
          <p className="no-stocks-message">You do not own any stocks yet.</p>
        ) : (
          <ul className="portfolio-list">
            {portfolio.map(stock => (
              <li key={stock.symbol}>
                <span className="stock-symbol">{stock.symbol}</span>
                <span className="stock-shares">{stock.shares} Shares</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};

export default Portfolio;