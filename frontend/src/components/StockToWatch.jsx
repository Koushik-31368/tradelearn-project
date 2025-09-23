// src/components/StockToWatch.jsx
import React from 'react';
import './StockToWatch.css';

const StockToWatch = ({ stock }) => {
  if (!stock) {
    return null;
  }
  
  // --- THIS IS THE FIX ---
  // We check if the price is actually a number before trying to format it.
  const isPriceNumber = typeof stock.price === 'number';
  const isPositive = isPriceNumber && stock.change > 0;

  return (
    <div className="stock-to-watch">
      <div className="badge">🔥 Stock to Watch Today</div>
      <div className="stock-info">
        <h3>{stock.name} ({stock.symbol})</h3>
        {/* We now display the price conditionally */}
        <p className={isPositive ? 'positive' : 'negative'}>
          {isPriceNumber ? `₹${stock.price.toFixed(2)}` : stock.price}
          {isPriceNumber && ` (${isPositive ? '+' : ''}${stock.change.toFixed(2)})`}
        </p>
      </div>
    </div>
  );
};

export default StockToWatch;