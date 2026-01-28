// src/components/StockSearch.jsx
import React, { useState } from 'react';
import { backendUrl } from '../utils/api';

const StockSearch = ({ onTrade }) => {
  const [symbol, setSymbol] = useState('');
  const [stockData, setStockData] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSearch = async () => {
    if (!symbol) return;

    setIsLoading(true);
    setError(null);

    try {
      const res = await fetch(
        backendUrl(`/api/games/data/${symbol.toUpperCase()}`)
      );

      if (!res.ok) throw new Error('Stock not found');

      const data = await res.json();
      setStockData(data);

    } catch (err) {
      setError('Failed to fetch stock');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div>
      <input
        value={symbol}
        onChange={(e) => setSymbol(e.target.value)}
        placeholder="Stock symbol"
      />
      <button onClick={handleSearch} disabled={isLoading}>
        {isLoading ? 'Loading...' : 'Search'}
      </button>

      {error && <p>{error}</p>}
      {stockData && <pre>{JSON.stringify(stockData, null, 2)}</pre>}
    </div>
  );
};

export default StockSearch;