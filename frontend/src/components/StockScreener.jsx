// src/components/StockScreener.jsx
import React, { useState } from 'react';
import './StockScreener.css';

const StockScreener = () => {
  const [symbol, setSymbol] = useState('');
  const [stockData, setStockData] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const RAPIDAPI_KEY = '5a88f051d3msh2db6f4199b0fd49p14dab4jsne5344846d18d';
  
  const fetchOptions = {
    method: 'GET',
    headers: {
      'X-RapidAPI-Key': RAPIDAPI_KEY,
      'X-RapidAPI-Host': 'twelve-data1.p.rapidapi.com'
    }
  };

  const handleSearch = async () => {
    if (!symbol) return;
    setIsLoading(true);
    setError(null);
    setStockData(null);
    try {
      const url = `https://twelve-data1.p.rapidapi.com/quote?symbol=${symbol}&exchange=NSE`;
      const response = await fetch(url, fetchOptions); // Pass the new options here
      const data = await response.json();

      if (data.code >= 400) {
        throw new Error(data.message || "Stock symbol not found.");
      }
      
      setStockData(data);

    } catch (err) {
      setError(err.message);
    }
    setIsLoading(false);
  };

  return (
    <div className="stock-screener">
      <div className="screener-search-bar">
        <input
          type="text"
          value={symbol}
          onChange={(e) => setSymbol(e.target.value.toUpperCase())}
          placeholder="Enter stock symbol (e.g., TCS)"
        />
        <button onClick={handleSearch} disabled={isLoading}>
          {isLoading ? 'Searching...' : 'Search'}
        </button>
      </div>

      {error && <div className="screener-error">{error}</div>}
      
      {stockData && (
        <div className="screener-results-single">
          <div className="result-card stats-card-full">
            <h3>{stockData.name} ({stockData.symbol})</h3>
            <ul>
              <li><span>Current Price:</span> <strong className={stockData.change >= 0 ? 'positive' : 'negative'}>₹{parseFloat(stockData.close).toFixed(2)}</strong></li>
              <li><span>Change:</span> <span className={stockData.change >= 0 ? 'positive' : 'negative'}>{parseFloat(stockData.change).toFixed(2)} ({parseFloat(stockData.percent_change).toFixed(2)}%)</span></li>
              <li><span>Market Cap:</span> <strong>{parseFloat(stockData.market_cap).toLocaleString('en-IN')}</strong></li>
              <li><span>Day's High:</span> {parseFloat(stockData.high).toFixed(2)}</li>
              <li><span>Day's Low:</span> {parseFloat(stockData.low).toFixed(2)}</li>
              <li><span>52-Week High:</span> {parseFloat(stockData.fifty_two_week.high).toFixed(2)}</li>
              <li><span>52-Week Low:</span> {parseFloat(stockData.fifty_two_week.low).toFixed(2)}</li>
            </ul>
          </div>
        </div>
      )}
    </div>
  );
};

export default StockScreener;