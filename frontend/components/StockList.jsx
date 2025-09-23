// src/components/StockList.jsx
import React from 'react';
import './StockList.css';

const StockList = ({ stocks, onStockSelect, searchQuery, onSearchChange }) => {
  return (
    <div className="stocklist-wrapper">
      <div className="stocklist-header">
        <h2>Market</h2>
        <div className="search-bar">
          <input
            type="text"
            placeholder="Search for a stock..."
            value={searchQuery}
            onChange={onSearchChange}
          />
        </div>
      </div>
      <table className="stock-table">
        <thead>
          <tr>
            <th>Symbol</th>
            <th>Price</th>
            <th>Change</th>
          </tr>
        </thead>
        <tbody>
          {stocks.map(stock => (
            <tr key={stock.symbol} onClick={() => onStockSelect(stock)}>
              <td>{stock.symbol}</td>
              <td>₹{stock.price.toFixed(2)}</td>
              <td className={stock.change > 0 ? 'positive' : 'negative'}>
                {stock.changePercent}%
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default StockList;