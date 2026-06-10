// src/components/StockSearch.jsx
import React, { useState } from 'react';

const StockSearch = ({ onTrade }) => {
  const [symbol, setSymbol] = useState('');
  const [stockData, setStockData] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSearch = async () => {
    if (!symbol) return;
    setIsLoading(true);
    setError(null);
    setStockData(null);

    try {
      const response = await fetch(`http://localhost:8080/api/games/data/${symbol.toUpperCase()}`);
      if (!response.ok) {
        throw new Error("Stock not found or API error.");
      }
      const data = await response.json();
      
      if (data["Error Message"] || data["Note"]) {
        throw new Error(data["Error Message"] || data["Note"]);
      }

      const timeSeries = data["Time Series (Daily)"];
      const latestDate = Object.keys(timeSeries)[0];
      const latestData = timeSeries[latestDate];
      
      setStockData({
        symbol: data["Meta Data"]["2. Symbol"],
        price: parseFloat(latestData["4. close"]),
        date: latestDate
      });
    } catch (err) {
      setError(err.message);
    }
    setIsLoading(false);
  };

  const handleTrade = (tradeType) => {
    if (!stockData || quantity <= 0) return;
    onTrade({
      stockSymbol: stockData.symbol,
      quantity: parseInt(quantity, 10),
      price: stockData.price,
      tradeType: tradeType
    });
  };

  return (
    <div className="stock-search">
      <div className="search-bar">
        <input
          type="text"
          value={symbol}
          onChange={(e) => setSymbol(e.target.value)}
          placeholder="Enter stock symbol (e.g., IBM)"
        />
        <button onClick={handleSearch} disabled={isLoading}>
          {isLoading ? '...' : 'Get Quote'}
        </button>
      </div>

      {error && <p className="error-message">{error}</p>}

      {stockData && (
        <div className="trade-controls">
          <h3>{stockData.symbol}</h3>
          <p>Price: <strong>₹{stockData.price.toFixed(2)}</strong> (as of {stockData.date})</p>
          <div className="trade-actions">
            <input
              type="number"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              min="1"
            />
            <button className="btn-buy" onClick={() => handleTrade('BUY')}>Buy</button>
            <button className="btn-sell" onClick={() => handleTrade('SELL')}>Sell</button>
            <button className="btn-short" onClick={() => handleTrade('SHORT')}>Short</button>
            {/* --- NEW BUTTON --- */}
            <button className="btn-cover" onClick={() => handleTrade('COVER')}>Cover</button>
          </div>
        </div>
      )}
    </div>
  );
};

export default StockSearch;