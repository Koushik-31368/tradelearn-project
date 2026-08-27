// src/components/simulator/Watchlist.jsx
import React, { useState, useMemo } from 'react';
import './Watchlist.css';

const SORT_OPTIONS = [
  { key: 'default', label: 'Default' },
  { key: 'change-desc', label: '% ▼' },
  { key: 'change-asc', label: '% ▲' },
  { key: 'alpha', label: 'A–Z' },
];

const Watchlist = ({ stocks, selectedSymbol, onSelect }) => {
  const [search, setSearch] = useState('');
  const [sortKey, setSortKey] = useState('default');

  const filtered = useMemo(() => {
    let list = stocks.filter(
      (s) =>
        s.symbol.toLowerCase().includes(search.toLowerCase()) ||
        s.name.toLowerCase().includes(search.toLowerCase())
    );
    if (sortKey === 'change-desc') list = [...list].sort((a, b) => b.change - a.change);
    else if (sortKey === 'change-asc') list = [...list].sort((a, b) => a.change - b.change);
    else if (sortKey === 'alpha') list = [...list].sort((a, b) => a.symbol.localeCompare(b.symbol));
    return list;
  }, [stocks, search, sortKey]);

  const maxAbs = useMemo(
    () => Math.max(...stocks.map((s) => Math.abs(s.change)), 0.01),
    [stocks]
  );

  return (
    <div className="watchlist">
      <div className="watchlist__header">
        <h3 className="watchlist__title">Watchlist</h3>
        <span className="watchlist__count">{stocks.length}</span>
      </div>

      <div className="watchlist__controls">
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
            aria-label="Search stocks"
          />
        </div>
        <div className="watchlist__sort-row" role="group" aria-label="Sort watchlist">
          {SORT_OPTIONS.map((opt) => (
            <button
              key={opt.key}
              className={`watchlist__sort-btn${sortKey === opt.key ? ' watchlist__sort-btn--active' : ''}`}
              onClick={() => setSortKey(opt.key)}
              aria-pressed={sortKey === opt.key}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      <div className="watchlist__list">
        {filtered.map((stock) => {
          const isSelected = stock.symbol === selectedSymbol;
          const isPositive = stock.change >= 0;
          const barWidth = Math.round((Math.abs(stock.change) / maxAbs) * 100);

          return (
            <div
              key={stock.symbol}
              className={`watchlist__item ${isSelected ? 'watchlist__item--active' : ''}`}
              onClick={() => onSelect(stock.symbol)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && onSelect(stock.symbol)}
              aria-label={`${stock.symbol} ${stock.change >= 0 ? '+' : ''}${stock.change.toFixed(2)}%`}
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
                {/* Mini momentum bar */}
                <div className="watchlist__bar-track" aria-hidden="true">
                  <div
                    className={`watchlist__bar-fill ${isPositive ? 'bar--up' : 'bar--down'}`}
                    style={{ width: `${barWidth}%` }}
                  />
                </div>
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
