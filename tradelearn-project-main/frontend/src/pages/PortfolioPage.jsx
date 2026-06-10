// src/pages/PortfolioPage.jsx
import React from 'react';
import { useOutletContext } from 'react-router-dom';
import './PortfolioPage.css';

const PortfolioPage = () => {
  // Get the shared portfolio and cash data from the parent layout
  const { portfolio, cashBalance } = useOutletContext();

  // Calculate portfolio values
  const investedValue = portfolio.reduce((sum, stock) => sum + (stock.avgPrice * stock.shares), 0);
  const holdingsValue = portfolio.reduce((sum, stock) => sum + (stock.price * stock.shares), 0);
  const totalPandL = holdingsValue - investedValue;
  const accountValue = cashBalance + holdingsValue;

  return (
    <div className="portfolio-page">
      <h1>Dashboard</h1>
      <div className="stats-cards">
        <div className="stat-card">
          <h3>Account Value</h3>
          <p>₹{accountValue.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</p>
        </div>
        <div className="stat-card">
          <h3>Total P&L</h3>
          <p className={totalPandL >= 0 ? 'positive' : 'negative'}>
            {totalPandL.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </p>
        </div>
        <div className="stat-card">
          <h3>Cash Balance</h3>
          <p>₹{cashBalance.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</p>
        </div>
      </div>
      
      <div className="performance-chart-placeholder">
        <h3>Performance History</h3>
        <p>A graph of your account value over time will be here.</p>
      </div>

      <div className="holdings-table">
        <h3>Your Holdings ({portfolio.length})</h3>
        <table>
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Shares</th>
              <th>Avg. Price</th>
              <th>Current Value</th>
            </tr>
          </thead>
          <tbody>
            {portfolio.length > 0 ? portfolio.map(stock => (
              <tr key={stock.symbol}>
                <td>{stock.symbol}</td>
                <td>{stock.shares}</td>
                <td>₹{stock.avgPrice.toFixed(2)}</td>
                <td>₹{(stock.price * stock.shares).toFixed(2)}</td>
              </tr>
            )) : (
              <tr>
                <td colSpan="4">You do not have any holdings.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default PortfolioPage;