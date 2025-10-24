// src/pages/SimulatorPage.jsx
import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import StockSearch from '../components/StockSearch';
import './SimulatorPage.css';

const SimulatorPage = () => {
  const { user } = useAuth();
  const [portfolio, setPortfolio] = useState(null);
  const [holdings, setHoldings] = useState([]); // 1. New state for holdings
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [tradeStatus, setTradeStatus] = useState(null);

  // 2. Create a separate function to fetch holdings
  const fetchHoldings = useCallback(async () => {
    if (!user) return;
    try {
      const response = await fetch(`http://localhost:8080/api/simulator/holdings?userId=${user.id}`);
      if (!response.ok) throw new Error('Could not fetch holdings');
      const data = await response.json();
      setHoldings(data);
    } catch (err) {
      console.error(err.message);
    }
  }, [user]);

  const fetchPortfolio = useCallback(async () => {
    if (!user) {
      setIsLoading(false);
      setError("Please log in to view your portfolio.");
      return;
    }
    setIsLoading(true);
    try {
      const response = await fetch(`http://localhost:8080/api/auth/my-portfolio?userId=${user.id}`);
      if (!response.ok) {
        const errData = await response.json();
        throw new Error(errData.message || "Could not fetch portfolio");
      }
      const data = await response.json();
      setPortfolio(data);
      // 3. After fetching portfolio, fetch holdings
      await fetchHoldings(); 
    } catch (err) {
      setError(err.message);
    } finally {
      setIsLoading(false);
    }
  }, [user, fetchHoldings]); // Add fetchHoldings as dependency

  useEffect(() => {
    fetchPortfolio();
  }, [fetchPortfolio]);

  const handleExecuteTrade = async (tradeDetails) => {
    if (!user) { /* ... (error handling) ... */ }
    setTradeStatus(null);
    try {
      const response = await fetch('http://localhost:8080/api/simulator/trade', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...tradeDetails, userId: user.id })
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "Trade failed");
      
      setTradeStatus({ error: false, message: "Trade executed successfully!" });
      setPortfolio(data); // Update the portfolio bar
      
      // 4. After a trade, refresh the holdings list
      await fetchHoldings(); 
      
    } catch (err) {
      setTradeStatus({ error: true, message: err.message });
    }
  };

  return (
    <div className="simulator-container">
      <header className="simulator-header">
        <h1>My Trading Simulator</h1>
      </header>
      <div className="portfolio-bar">
        {/* ... (portfolio bar remains the same) ... */}
      </div>

      <div className="simulator-body">
        <main className="trading-column">
          <h2>Trading Terminal</h2>
          <StockSearch onTrade={handleExecuteTrade} />
          {tradeStatus && (
            <p className={tradeStatus.error ? 'error-message' : 'success-message'}>
              {tradeStatus.message}
            </p>
          )}
        </main>
        
        <aside className="holdings-column">
          <h2>My Holdings</h2>
          {/* 5. New section to display holdings */}
          <div className="holdings-list">
            {holdings.length === 0 ? (
              <p>You do not own any stocks.</p>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Stock</th>
                    <th>Qty</th>
                    <th>Avg. Price</th>
                  </tr>
                </thead>
                <tbody>
                  {holdings.map(holding => (
                    <tr key={holding.id}>
                      <td>{holding.stockSymbol}</td>
                      <td>{holding.quantity}</td>
                      <td>₹{holding.averagePurchasePrice.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
};

export default SimulatorPage;