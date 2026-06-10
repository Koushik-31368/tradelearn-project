// src/components/simulator/TradingPanel.jsx
import React, { useState, useEffect, useRef } from 'react';
import './TradingPanel.css';

const TradingPanel = ({ stock, portfolio, onTrade }) => {
  const [quantity, setQuantity] = useState(1);
  const [activeType, setActiveType] = useState('BUY');
  const [animating, setAnimating] = useState(false);
  const [toast, setToast] = useState(null);
  const toastTimer = useRef(null);

  const holding = stock && portfolio.holdings[stock.symbol];
  const holdingQty = holding ? holding.qty : 0;
  const totalCost = stock ? stock.price * quantity : 0;

  useEffect(() => {
    setQuantity(1);
  }, [stock?.symbol]);

  const showToast = (message, type) => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
    setToast({ message, type });
    toastTimer.current = setTimeout(() => setToast(null), 3000);
  };

  const handleTrade = () => {
    if (!stock || quantity <= 0) return;

    setAnimating(true);
    setTimeout(() => {
      const result = onTrade({
        symbol: stock.symbol,
        price: stock.price,
        quantity,
        type: activeType,
      });

      setAnimating(false);

      if (result.success) {
        showToast(result.message, 'success');
        setQuantity(1);
      } else {
        showToast(result.message, 'error');
      }
    }, 400);
  };

  const presets = [1, 5, 10, 25, 50, 100];

  if (!stock) {
    return (
      <div className="trading-panel">
        <div className="trading-panel__header">
          <h3 className="trading-panel__title">Trade</h3>
        </div>
        <div className="trading-panel__empty">
          <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="#30363d" strokeWidth="1.5">
            <path d="M3 3v18h18" />
            <path d="M7 16l4-4 4 4 5-5" />
          </svg>
          <p>Select a stock from the watchlist</p>
        </div>
      </div>
    );
  }

  const isPositive = stock.change >= 0;

  return (
    <div className="trading-panel">
      {/* Toast */}
      {toast && (
        <div className={`trading-toast trading-toast--${toast.type}`}>
          <span className="trading-toast__icon">
            {toast.type === 'success' ? '✓' : '✕'}
          </span>
          {toast.message}
        </div>
      )}

      <div className="trading-panel__header">
        <h3 className="trading-panel__title">Trade</h3>
      </div>

      {/* Stock Info */}
      <div className="trading-panel__stock-info">
        <div className="trading-panel__stock-name">
          <span className="trading-panel__symbol">{stock.symbol}</span>
          <span className="trading-panel__fullname">{stock.name}</span>
        </div>
        <div className="trading-panel__price-block">
          <span className="trading-panel__current-price">
            ₹{stock.price.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
          </span>
          <span className={`trading-panel__change ${isPositive ? 'change--up' : 'change--down'}`}>
            {isPositive ? '+' : ''}{stock.change.toFixed(2)}%
          </span>
        </div>
      </div>

      {/* Position Info */}
      {holdingQty !== 0 && (
        <div className="trading-panel__position">
          <span className="trading-panel__position-label">Current Position</span>
          <span className={`trading-panel__position-value ${holdingQty > 0 ? 'text-profit' : 'text-loss'}`}>
            {holdingQty > 0 ? '+' : ''}{holdingQty} shares
          </span>
        </div>
      )}

      {/* Trade Type Tabs */}
      <div className="trading-panel__tabs">
        {['BUY', 'SELL', 'SHORT', 'COVER'].map((type) => (
          <button
            key={type}
            className={`trading-panel__tab ${activeType === type ? `trading-panel__tab--${type.toLowerCase()}` : ''}`}
            onClick={() => setActiveType(type)}
          >
            {type}
          </button>
        ))}
      </div>

      {/* Quantity */}
      <div className="trading-panel__field">
        <label className="trading-panel__label">Quantity</label>
        <div className="trading-panel__qty-row">
          <button
            className="trading-panel__qty-btn"
            onClick={() => setQuantity(Math.max(1, quantity - 1))}
          >
            −
          </button>
          <input
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
            className="trading-panel__qty-input"
          />
          <button
            className="trading-panel__qty-btn"
            onClick={() => setQuantity(quantity + 1)}
          >
            +
          </button>
        </div>
        <div className="trading-panel__presets">
          {presets.map((p) => (
            <button
              key={p}
              className={`trading-panel__preset ${quantity === p ? 'trading-panel__preset--active' : ''}`}
              onClick={() => setQuantity(p)}
            >
              {p}
            </button>
          ))}
        </div>
      </div>

      {/* Order Summary */}
      <div className="trading-panel__summary">
        <div className="trading-panel__summary-row">
          <span>Order Type</span>
          <span>{activeType} · Market</span>
        </div>
        <div className="trading-panel__summary-row">
          <span>Price</span>
          <span>₹{stock.price.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
        </div>
        <div className="trading-panel__summary-row">
          <span>Quantity</span>
          <span>{quantity}</span>
        </div>
        <div className="trading-panel__summary-row trading-panel__summary-row--total">
          <span>Estimated Total</span>
          <span>₹{totalCost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
        </div>
      </div>

      {/* Execute Button */}
      <button
        className={`trading-panel__execute trading-panel__execute--${activeType.toLowerCase()} ${animating ? 'trading-panel__execute--animating' : ''}`}
        onClick={handleTrade}
        disabled={animating}
      >
        {animating ? (
          <span className="trading-panel__spinner" />
        ) : (
          `${activeType} ${stock.symbol}`
        )}
      </button>

      {/* Cash Reminder */}
      <div className="trading-panel__cash">
        Available: ₹{portfolio.cash.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
      </div>
    </div>
  );
};

export default TradingPanel;
