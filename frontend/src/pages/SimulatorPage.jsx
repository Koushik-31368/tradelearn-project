// src/pages/SimulatorPage.jsx
import React, { useEffect, useState, useCallback } from 'react';
import axios from 'axios';
import { backendUrl, authHeaders, jsonAuthHeaders } from '../utils/api';
import { useAuth } from '../context/AuthContext';
import { indianStocks, getStockBySymbol } from '../data/indianStocks';
import './SimulatorPage.css';

const SimulatorPage = () => {
  const { user } = useAuth();

  const [balance, setBalance] = useState(0);
  const [holdings, setHoldings] = useState([]);
  const [tradeHistory, setTradeHistory] = useState([]);

  const [selectedStock, setSelectedStock] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [tradeType, setTradeType] = useState('buy');
  const [message, setMessage] = useState({ text: '', type: '' });

  /* ========================
     LOAD PORTFOLIO
  ========================= */
  const loadUserPortfolio = useCallback(async () => {
    if (!user) return;

    try {
      const res = await axios.get(
        backendUrl(`/api/simulator/portfolio?userId=${user.id}`),
        { headers: authHeaders() }
      );

      const portfolio = res.data;
      setBalance(portfolio.virtualCash);

      const formattedHoldings = portfolio.holdings.map(h => {
        const stock = getStockBySymbol(h.stockSymbol);
        const currentValue = h.quantity * stock.price;
        const pnl = currentValue - h.quantity * h.averagePurchasePrice;

        return {
          symbol: h.stockSymbol,
          quantity: h.quantity,
          avgPrice: h.averagePurchasePrice.toFixed(2),
          currentPrice: stock.price.toFixed(2),
          currentValue: currentValue.toFixed(2),
          pnl: pnl.toFixed(2),
        };
      });

      setHoldings(formattedHoldings);
    } catch (err) {
      console.error('Error loading portfolio', err);
    }
  }, [user]);

  /* ========================
     LOAD TRADE HISTORY
  ========================= */
  const loadTradeHistory = useCallback(async () => {
    if (!user) return;

    try {
      const res = await axios.get(
        backendUrl(`/api/trades/user/${user.id}`),
        { headers: authHeaders() }
      );
      setTradeHistory(res.data.reverse());
    } catch (err) {
      console.error('Error loading trade history', err);
    }
  }, [user]);

  useEffect(() => {
    if (user) {
      loadUserPortfolio();
      loadTradeHistory();
    }
  }, [user, loadUserPortfolio, loadTradeHistory]);

  /* ========================
     EXECUTE TRADE
  ========================= */
  const executeTrade = async () => {
    if (!user || !selectedStock) {
      setMessage({ text: 'Select a stock first', type: 'error' });
      return;
    }

    const totalCost = quantity * selectedStock.price;

    if (tradeType === 'buy' && totalCost > balance) {
      setMessage({ text: 'Insufficient balance', type: 'error' });
      return;
    }

    if (tradeType === 'sell') {
      const holding = holdings.find(h => h.symbol === selectedStock.symbol);
      if (!holding || holding.quantity < quantity) {
        setMessage({ text: 'Not enough stocks to sell', type: 'error' });
        return;
      }
    }

    try {
      await axios.post(
        backendUrl('/api/simulator/trade'),
        {
          userId: user.id,
          stockSymbol: selectedStock.symbol,
          tradeType: tradeType.toUpperCase(),
          quantity,
          price: selectedStock.price,
        },
        { headers: jsonAuthHeaders() }
      );

      setMessage({ text: 'Trade successful!', type: 'success' });
      setQuantity(1);
      loadUserPortfolio();
      loadTradeHistory();

    } catch (err) {
      setMessage({ text: 'Trade failed', type: 'error' });
    }
  };

  /* ========================
     UI
  ========================= */
  return (
    <div className="simulator-page container">
      <h1>Stock Simulator</h1>

      {message.text && (
        <div className={`alert alert-${message.type}`}>
          {message.text}
        </div>
      )}

      <div className="balance-card">
        <h3>Virtual Balance</h3>
        <p>₹{balance.toFixed(2)}</p>
      </div>

      <div className="simulator-layout">
        {/* STOCK LIST */}
        <div className="stock-list">
          <h3>Market</h3>
          {indianStocks.map(stock => (
            <div
              key={stock.symbol}
              className={`stock-item ${selectedStock?.symbol === stock.symbol ? 'active' : ''}`}
              onClick={() => setSelectedStock(stock)}
            >
              <strong>{stock.symbol}</strong>
              <span>₹{stock.price.toFixed(2)}</span>
            </div>
          ))}
        </div>

        {/* TRADE PANEL */}
        <div className="trade-panel">
          <h3>Trade</h3>

          {selectedStock && (
            <>
              <p><strong>{selectedStock.name}</strong></p>
              <p>Price: ₹{selectedStock.price.toFixed(2)}</p>

              <select value={tradeType} onChange={e => setTradeType(e.target.value)}>
                <option value="buy">Buy</option>
                <option value="sell">Sell</option>
              </select>

              <input
                type="number"
                min="1"
                value={quantity}
                onChange={e => setQuantity(Number(e.target.value))}
              />

              <button className="btn btn-primary" onClick={executeTrade}>
                Execute Trade
              </button>
            </>
          )}
        </div>
      </div>

      {/* HOLDINGS */}
      <div className="panel">
        <h3>My Holdings</h3>
        {holdings.length === 0 ? (
          <p>No holdings yet</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Stock</th>
                <th>Qty</th>
                <th>Avg</th>
                <th>Current</th>
                <th>P/L</th>
              </tr>
            </thead>
            <tbody>
              {holdings.map(h => (
                <tr key={h.symbol}>
                  <td>{h.symbol}</td>
                  <td>{h.quantity}</td>
                  <td>₹{h.avgPrice}</td>
                  <td>₹{h.currentPrice}</td>
                  <td className={Number(h.pnl) >= 0 ? 'badge-pos' : 'badge-neg'}>
                    ₹{h.pnl}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* TRADE HISTORY */}
      <div className="panel">
        <h3>Trade History</h3>
        {tradeHistory.length === 0 ? (
          <p>No trades yet</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Stock</th>
                <th>Type</th>
                <th>Qty</th>
                <th>Price</th>
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              {tradeHistory.map(t => (
                <tr key={t.id}>
                  <td>{t.symbol}</td>
                  <td>{t.tradeType}</td>
                  <td>{t.quantity}</td>
                  <td>₹{t.price}</td>
                  <td>{new Date(t.timestamp).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

export default SimulatorPage;