// src/components/MarketMovers.jsx
import React, { useState, useEffect } from 'react';
import './MarketMovers.css';

const MarketMovers = () => {
  const [movers, setMovers] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const ALPHA_VANTAGE_API_KEY = 'B4OPN84YU64DH1AN'; // Your key

  useEffect(() => {
    const fetchMovers = async () => {
      setIsLoading(true);
      try {
        const url = `https://www.alphavantage.co/query?function=TOP_GAINERS_LOSERS&apikey=${ALPHA_VANTAGE_API_KEY}`;
        const response = await fetch(url);
        const data = await response.json();

        const gainers = (data.top_gainers || []).slice(0, 5); // Get top 5 gainers
        const losers = (data.top_losers || []).slice(0, 5); // Get top 5 losers
        setMovers([...gainers, ...losers]);
      } catch (error) {
        console.error("Error fetching market movers:", error);
      }
      setIsLoading(false);
    };

    fetchMovers();
  }, []);

  if (isLoading) {
    return <div className="movers-container"><h3>Loading Top Movers...</h3></div>;
  }

  return (
    <div className="movers-container">
      <h3>Top Movers Today</h3>
      <ul className="movers-list">
        {movers.map((stock, index) => (
          <li key={index} className="mover-item">
            <span className="mover-symbol">{stock.ticker}</span>
            <span className={parseFloat(stock.change_amount) >= 0 ? 'positive' : 'negative'}>
              {stock.change_percentage}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
};

export default MarketMovers;