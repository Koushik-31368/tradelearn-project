import React, { useState, useEffect, useCallback } from 'react';
import { indianStocks, getStockBySymbol } from '../data/indianStocks';
import { useAuth } from '../context/AuthContext';
import axios from 'axios';
import './SimulatorPage.css';
import '../styles/theme.css'; // ensure theme is loaded

const INITIAL_BALANCE = 100000;
const API_URL = process.env.REACT_APP_API_URL || 'https://tradelearn-project-production.up.railway.app';

const SimulatorPage = () => {
  const { user } = useAuth();
  const [balance, setBalance] = useState(INITIAL_BALANCE);
  const [holdings, setHoldings] = useState([]);
  const [tradeHistory, setTradeHistory] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedStock, setSelectedStock] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [tradeType, setTradeType] = useState('buy');
  const [message, setMessage] = useState({ text: '', type: '' });

  const loadUserPortfolio = useCallback(async () => {
    try {
      const response = await axios.get(`${API_URL}/api/simulator/portfolio?userId=${user.id}`);
      setPortfolioData(response.data);
    } catch (error) {
      console.error('Error loading portfolio:', error);
    }
  }, [user]);

  const loadTradeHistory = useCallback(async () => {
    try {
      const response = await axios.get(`${API_URL}/api/trades/user/${user.id}`);
      setTradeHistory(response.data.reverse());
    } catch (error) {
      console.error('Error loading trade history:', error);
    }
  }, [user]);

  useEffect(() => {
    if (user) {
      loadUserPortfolio();
      loadTradeHistory();
    }
  }, [user, loadUserPortfolio, loadTradeHistory]);

  const setPortfolioData = (portfolio) => {
    setBalance(portfolio.virtualCash);
    const holdingsArray = portfolio.holdings.map(holding => {
      const stock = getStockBySymbol(holding.stockSymbol);
      const currentValue = holding.quantity * stock.price;
      const profitLoss = currentValue - (holding.quantity * holding.averagePurchasePrice);
      const profitLossPercent = (profitLoss / (holding.quantity * holding.averagePurchasePrice)) * 100;
      return {
        symbol: holding.stockSymbol,
        name: stock.name,
        quantity: holding.quantity,
        avgPrice: holding.averagePurchasePrice.toFixed(2),
        currentPrice: stock.price,
        currentValue: currentValue.toFixed(2),
        profitLoss: profitLoss.toFixed(2),
        profitLossPercent: profitLossPercent.toFixed(2)
      };
    });
    setHoldings(holdingsArray);
  };

  const handleSearch = (query) => {
    setSearchQuery(query);
    if (query) {
      const stock = indianStocks.find(s => 
        s.symbol.toLowerCase().includes(query.toLowerCase()) ||
        s.name.toLowerCase().includes(query.toLowerCase())
      );
      setSelectedStock(stock || null);
    } else {
      setSelectedStock(null);
    }
  };

  const executeTrade = async () => {
    if (!selectedStock || !user) {
      setMessage({ text: 'Please login and select a stock', type: 'error' });
      return;
    }

    const totalCost = quantity * selectedStock.price;

    if (tradeType === 'buy' && totalCost > balance) {
      setMessage({ text: 'Insufficient balance!', type: 'error' });
      return;
    }

    if (tradeType === 'sell') {
      const holding = holdings.find(h => h.symbol === selectedStock.symbol);
      if (!holding || holding.quantity < quantity) {
        setMessage({ text: 'Insufficient holdings to sell!', type: 'error' });
        return;
      }
    }

    try {
      const tradeData = {
        userId: user.id,
        stockSymbol: selectedStock.symbol,
        name: selectedStock.name,
        tradeType: tradeType.toUpperCase(),
        quantity: quantity,
        price: selectedStock.price
      };

      await axios.post(`${API_URL}/api/simulator/trade`, tradeData);
      
      setMessage({ text: `${tradeType === 'buy' ? 'Bought' : 'Sold'} ${quantity} shares of ${selectedStock.symbol} successfully!`, type: 'success' });
      
      loadUserPortfolio();
      loadTradeHistory();
      
      setQuantity(1);
      setSearchQuery('');
      setSelectedStock(null);
    } catch (error) {
      const errorMessage = error.response?.data?.message || 'Trade failed. Please try again.';
      setMessage({ text: errorMessage, type: 'error' });
      console.error('Trade error:', error);
    }
  };

  const portfolioValue = holdings.reduce((sum, h) => sum + parseFloat(h.currentValue), 0);
  const totalValue = balance + portfolioValue;
  const totalProfitLoss = totalValue - INITIAL_BALANCE;
  const totalProfitLossPercent = (totalProfitLoss / INITIAL_BALANCE) * 100;

  if (!user) {
    return (
      <div className="simulator-page">
        <div className="container">
          <div className="login-required">
            <h2>Login Required</h2>
            <p>Please login to access the trading simulator</p>
            <a href="/login" className="login-link">Go to Login</a>
          </div>
        </div>
      </div>
    );
  }

  return (
<div className="simulator-page">
  <div className="container">

    {/* Header */}
    <div className="simulator-header">
      <h1>Trading Simulator</h1>
      <p>Practice with virtual money and track your portfolio.</p>
    </div>

    {/* Portfolio Summary */}
    <div className="portfolio-summary">
      <div className="summary-card">
        <h3>Available Balance</h3>
        <p className="balance">₹{balance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</p>
      </div>
      <div className="summary-card">
        <h3>Holdings Value</h3>
        <p className="holdings-value">₹{portfolioValue.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</p>
      </div>
      <div className="summary-card">
        <h3>Total Portfolio</h3>
        <p className="total-value">₹{totalValue.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</p>
      </div>
      <div className="summary-card">
        <h3>Total P&L</h3>
        <p className={`profit-loss ${totalProfitLoss >= 0 ? 'positive' : 'negative'}`}>
          ₹{totalProfitLoss.toLocaleString('en-IN', { minimumFractionDigits: 2 })} ({totalProfitLossPercent.toFixed(2)}%)
        </p>
      </div>
    </div>

    {/* Trading Interface */}
    <div className="trading-section">
      <h2>Search and Trade</h2>

      <div className="search-bar">
        <input
          className="input"
          type="text"
          placeholder="Search stock by symbol or name (e.g., TCS, Infosys)"
          value={searchQuery}
          onChange={(e) => handleSearch(e.target.value)}
        />
      </div>

      {selectedStock && (
        <div className="stock-details">
          <div className="stock-info panel">
            <h3>{selectedStock.symbol}</h3>
            <p className="stock-name">{selectedStock.name}</p>
            <p className="stock-price">₹{selectedStock.price.toFixed(2)}</p>
            <p className={`stock-change ${selectedStock.change >= 0 ? 'positive' : 'negative'}`}>
              {selectedStock.change >= 0 ? '+' : ''}{Math.abs(selectedStock.change).toFixed(2)}%
            </p>
          </div>

          <div className="trade-form panel">
            <div className="trade-type-selector">
              <button
                className={`trade-type-btn ${tradeType === 'buy' ? 'active buy' : ''}`}
                onClick={() => setTradeType('buy')}
              >Buy</button>
              <button
                className={`trade-type-btn ${tradeType === 'sell' ? 'active sell' : ''}`}
                onClick={() => setTradeType('sell')}
              >Sell</button>
            </div>

            <div className="quantity-input">
              <label>Quantity</label>
              <input
                className="input"
                type="number"
                min="1"
                value={quantity}
                onChange={(e) => setQuantity(parseInt(e.target.value) || 1)}
              />
            </div>

            <div className="trade-summary">
              <p>Total: ₹{(quantity * selectedStock.price).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</p>
            </div>

            <button className={`execute-btn ${tradeType}`} onClick={executeTrade}>
              {tradeType === 'buy' ? 'Buy' : 'Sell'} {quantity} shares
            </button>
          </div>
        </div>
      )}

      {message.text && (
        <div className={`message ${message.type}`}>{message.text}</div>
      )}

      {!selectedStock && searchQuery && (
        <p className="no-results">No stock found for “{searchQuery}”.</p>
      )}

      {!selectedStock && !searchQuery && (
        <div className="stock-suggestions">
          <h3>Popular Stocks</h3>
          <div className="suggestions-grid">
            {indianStocks.slice(0, 6).map(stock => (
              <div
                key={stock.symbol}
                className="suggestion-card"
                onClick={() => {
                  setSearchQuery(stock.symbol);
                  setSelectedStock(stock);
                }}
              >
                <h4>{stock.symbol}</h4>
                <p className="stock-name">{stock.name}</p>
                <p className="price">₹{stock.price.toFixed(2)}</p>
                <p className={`change ${stock.change >= 0 ? 'positive' : 'negative'}`}>
                  {stock.change >= 0 ? '+' : ''}{Math.abs(stock.change).toFixed(2)}%
                </p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>

    {/* Holdings */}
    <div className="holdings-section">
      <h2>Your Holdings</h2>
      {holdings.length > 0 ? (
        <div className="holdings-table panel">
          <table className="table">
            <thead>
              <tr>
                <th>Symbol</th>
                <th>Qty</th>
                <th>Avg Price</th>
                <th>Current Price</th>
                <th>Current Value</th>
                <th>P&L</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {holdings.map(holding => (
                <tr key={holding.symbol}>
                  <td><strong>{holding.symbol}</strong></td>
                  <td>{holding.quantity}</td>
                  <td>₹{holding.avgPrice}</td>
                  <td>₹{holding.currentPrice.toFixed(2)}</td>
                  <td>₹{holding.currentValue}</td>
                  <td className={parseFloat(holding.profitLoss) >= 0 ? 'badge-pos' : 'badge-neg'}>
                    ₹{holding.profitLoss} ({holding.profitLossPercent}%)
                  </td>
                  <td>
                    <button
                      className="sell-btn"
                      onClick={() => {
                        setSearchQuery(holding.symbol);
                        setSelectedStock(getStockBySymbol(holding.symbol));
                        setTradeType('sell');
                        setQuantity(holding.quantity);
                      }}
                    >
                      Sell
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="no-holdings">No holdings yet.</p>
      )}
    </div>

    {/* Trade History */}
    <div className="history-section">
      <h2>Trade History</h2>
      {tradeHistory.length > 0 ? (
        <div className="history-table panel">
          <table className="table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Type</th>
                <th>Symbol</th>
                <th>Qty</th>
                <th>Price</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {tradeHistory.map((trade, index) => (
                <tr key={index}>
                  <td>{new Date(trade.timestamp).toLocaleDateString()}</td>
                  <td className={trade.type.toLowerCase()}>{trade.type}</td>
                  <td><strong>{trade.symbol}</strong></td>
                  <td>{trade.quantity}</td>
                  <td>₹{trade.price.toFixed(2)}</td>
                  <td>₹{(trade.quantity * trade.price).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="no-history">No trades yet.</p>
      )}
    </div>

  </div>
</div>
  );
};

export default SimulatorPage;
