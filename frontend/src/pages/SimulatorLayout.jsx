// src/pages/SimulatorLayout.jsx
import React, { useState, useEffect } from 'react';
import { Outlet, NavLink } from 'react-router-dom';
import FeedbackModal from '../components/FeedbackModal'; // Import the new modal
import './SimulatorLayout.css';

const SimulatorLayout = () => {
  const [portfolio, setPortfolio] = useState(() => JSON.parse(localStorage.getItem('userPortfolio')) || []);
  const [cashBalance, setCashBalance] = useState(() => JSON.parse(localStorage.getItem('userCashBalance')) || 100000);

  // --- NEW STATE for the Modal ---
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [feedbackData, setFeedbackData] = useState(null);
  const NEWS_API_KEY = 'ed42aa102bf84e61aaf6c1684c4970c6'; // Your key from newsapi.org

  useEffect(() => {
    localStorage.setItem('userPortfolio', JSON.stringify(portfolio));
    localStorage.setItem('userCashBalance', JSON.stringify(cashBalance));
  }, [portfolio, cashBalance]);

  const fetchNewsForStock = async (stock) => {
    try {
      const query = encodeURIComponent(`(${stock.name} OR ${stock.symbol}) AND (stock OR market)`);
      const url = `https://newsapi.org/v2/everything?q=${query}&language=en&sortBy=publishedAt&pageSize=5&apiKey=${NEWS_API_KEY}`;
      const response = await fetch(url);
      const data = await response.json();
      return data.articles || [];
    } catch (error) {
      console.error("Failed to fetch news for feedback:", error);
      return []; // Return empty array on failure
    }
  };

  const showFeedback = async (tradeType, stock, shares) => {
    const news = await fetchNewsForStock(stock);
    setFeedbackData({ tradeType, stock, shares, news });
    setIsModalOpen(true);
  };

  const handleBuy = (stockToBuy, shares) => {
    const cost = stockToBuy.price * shares;
    if (cost > cashBalance) { alert("Not enough cash!"); return; }
    setCashBalance(cashBalance - cost);
    // ... (rest of the buy logic is the same)
    const existingHolding = portfolio.find(s => s.symbol === stockToBuy.symbol);
    if (existingHolding) {
      const totalShares = existingHolding.shares + shares;
      const totalCost = (existingHolding.avgPrice * existingHolding.shares) + cost;
      const newAvgPrice = totalCost / totalShares;
      const updatedPortfolio = portfolio.map(s =>
        s.symbol === stockToBuy.symbol ? { ...s, shares: totalShares, avgPrice: newAvgPrice } : s
      );
      setPortfolio(updatedPortfolio);
    } else {
      const newHolding = { ...stockToBuy, shares: shares, avgPrice: stockToBuy.price };
      setPortfolio([...portfolio, newHolding]);
    }
    // Show feedback after the trade
    showFeedback('Buy', stockToBuy, shares);
  };

  const handleSell = (stockToSell, shares) => {
    const holding = portfolio.find(s => s.symbol === stockToSell.symbol);
    if (!holding || holding.shares < shares) { alert("You don't own enough shares to sell!"); return; }
    const value = stockToSell.price * shares;
    setCashBalance(cashBalance + value);
    // ... (rest of the sell logic is the same)
    const updatedPortfolio = portfolio.map(s =>
      s.symbol === stockToSell.symbol ? { ...s, shares: s.shares - shares } : s
    ).filter(s => s.shares > 0);
    setPortfolio(updatedPortfolio);
    // Show feedback after the trade
    showFeedback('Sell', stockToSell, shares);
  };

  return (
    <div className="simulator-container">
      <nav className="simulator-nav">
        <NavLink to="portfolio">Portfolio</NavLink>
        <NavLink to="stocks">Stocks</NavLink>
        <NavLink to="overview">Overview</NavLink>
      </nav>
      <main className="simulator-content">
        <Outlet context={{ portfolio, cashBalance, handleBuy, handleSell }} />
      </main>
      
      {/* Add the Modal component here */}
      <FeedbackModal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
        feedbackData={feedbackData} 
      />
    </div>
  );
};

export default SimulatorLayout;