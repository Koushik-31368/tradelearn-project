// src/components/StockDetailView.jsx
import React, { useState, useEffect } from 'react';
import './StockDetailView.css';

const StockDetailView = ({ stock, onBuy, onSell }) => {
  // --- NEW STATE for Order Types ---
  const [shares, setShares] = useState(1);
  const [orderType, setOrderType] = useState('MARKET'); // Default to Market Order
  const [limitPrice, setLimitPrice] = useState('');    // For the limit price input

  // When the selected stock changes, update the default limit price
  useEffect(() => {
    if (stock && typeof stock.price === 'number') {
      setLimitPrice(stock.price.toFixed(2));
    }
  }, [stock]);


  if (!stock) {
    return <div className="stock-detail-container"><p>Select a stock to see details.</p></div>;
  }
  
  const handleSharesChange = (e) => {
    const value = e.target.value ? parseInt(e.target.value, 10) : 1;
    setShares(value > 0 ? value : 1);
  };

  const isPriceNumber = typeof stock.price === 'number';
  const isPositive = isPriceNumber && stock.change > 0;

  return (
    <div className="stock-detail-container">
      <div className="stock-detail-header">
        <h2>{stock.name} ({stock.symbol})</h2>
        <div className="price-info">
          <span className="current-price">
            {isPriceNumber ? `₹${stock.price.toFixed(2)}` : stock.price}
          </span>
          <span className={isPositive ? 'positive' : 'negative'}>
            {isPriceNumber ? `${isPositive ? '+' : ''}${stock.change.toFixed(2)} (${stock.changePercent}%)` : ''}
          </span>
        </div>
      </div>

      <div className="chart-placeholder">
        <p>Price Chart Will Be Displayed Here</p>
      </div>

      {/* --- NEW Order Type and Trading Actions UI --- */}
      <div className="trade-controls">
        <div className="order-type-selector">
          <button 
            className={orderType === 'MARKET' ? 'active' : ''} 
            onClick={() => setOrderType('MARKET')}
          >
            Market
          </button>
          <button 
            className={orderType === 'LIMIT' ? 'active' : ''} 
            onClick={() => setOrderType('LIMIT')}
          >
            Limit
          </button>
        </div>

        <div className="trade-actions">
          <div className="trade-input">
            <label htmlFor="shares">Shares</label>
            <input 
              type="number" 
              id="shares" 
              value={shares} 
              onChange={handleSharesChange} 
              min="1" 
            />
          </div>

          {/* This input will only show up for Limit orders */}
          {orderType === 'LIMIT' && (
            <div className="trade-input">
              <label htmlFor="limit-price">Limit Price</label>
              <input 
                type="number" 
                id="limit-price"
                value={limitPrice}
                onChange={(e) => setLimitPrice(e.target.value)}
              />
            </div>
          )}

          <button className="buy-button" onClick={() => onBuy(stock, shares)} disabled={!isPriceNumber}>Buy</button>
          <button className="sell-button" onClick={() => onSell(stock, shares)} disabled={!isPriceNumber}>Sell</button>
        </div>
      </div>
    </div>
  );
};

export default StockDetailView;