// src/components/simulator/Watchlist.jsx
import React, { useState } from 'react';
import './Watchlist.css';

const Watchlist = ({ stocks, selectedSymbol, onSelect }) => {
  const [search, setSearch] = useState('');

  const filtered = stocks.filter(
    (s) =>
      s.symbol.toLowerCase().includes(search.toLowerCase()) ||
      s.name.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="watchlist">
      <div className="watchlist__header">
        <h3 className="watchlist__title">Watchlist</h3>
        <span className="watchlist__count">{stocks.length}</span>
      </div>

      <div className="watchlist__search">
        <svg className="watchlist__search-icon" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#8b949e" strokeWidth="2">
          <circle cx="11" cy="11" r="8" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
        <input
          type="text"
          placeholder="Search stocks..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="watchlist__input"
        />
      </div>

      <div className="watchlist__list">
        {filtered.map((stock) => {
          const isSelected = stock.symbol === selectedSymbol;
          const isPositive = stock.change >= 0;

          return (
            <div
              key={stock.symbol}
              className={`watchlist__item ${isSelected ? 'watchlist__item--active' : ''}`}
              onClick={() => onSelect(stock.symbol)}
            >
              <div className="watchlist__item-left">
                <span className="watchlist__symbol">{stock.symbol}</span>
                <span className="watchlist__name">{stock.name}</span>
              </div>
              <div className="watchlist__item-right">
                <span className="watchlist__price">₹{stock.price.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                <span className={`watchlist__change ${isPositive ? 'change--up' : 'change--down'}`}>
                  {isPositive ? '+' : ''}{stock.change.toFixed(2)}%
                </span>
              </div>
            </div>
          );
        })}

        {filtered.length === 0 && (
          <div className="watchlist__empty">No stocks found</div>
        )}
      </div>
    </div>
  );
};

export default Watchlist;
