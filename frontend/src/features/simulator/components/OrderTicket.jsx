// src/components/simulator/OrderTicket.jsx
import React, { useState, useEffect, useRef } from 'react';
import './TradingPanel.css';

const OrderTicket = ({ stock, portfolio, onTrade }) => {
  const [activeType, setActiveType] = useState('BUY');
  const [quantity, setQuantity] = useState(1);
  const [stopLoss, setStopLoss] = useState('');
  const [targetPrice, setTargetPrice] = useState('');
  const [thesisCategory, setThesisCategory] = useState('');
  const [thesis, setThesis] = useState('');
  const [animating, setAnimating] = useState(false);
  const [toast, setToast] = useState(null);
  const toastTimer = useRef(null);


  // Calculate dynamic total and risk
  const price = stock ? stock.price : 0;
  const totalCost = price * quantity;
  
  const slValue = parseFloat(stopLoss);
  const tpValue = parseFloat(targetPrice);
  
  const riskPerShare = slValue > 0 ? Math.abs(price - slValue) : 0;
  const totalRisk = riskPerShare * quantity;
  const riskPct = portfolio.cash > 0 ? (totalRisk / portfolio.cash) * 100 : 0;
  
  const rewardPerShare = tpValue > 0 ? Math.abs(tpValue - price) : 0;
  const totalReward = rewardPerShare * quantity;
  const riskRewardRatio = totalRisk > 0 ? (totalReward / totalRisk).toFixed(2) : '0.00';

  useEffect(() => {
    setQuantity(1);
    setStopLoss('');
    setTargetPrice('');
    setThesisCategory('');
    setThesis('');
  }, [stock?.symbol]);

  const showToast = (message, type) => {
    if (toastTimer.current) clearTimeout(toastTimer.current);
    setToast({ message, type });
    toastTimer.current = setTimeout(() => setToast(null), 3000);
  };

  const handleTrade = async () => {
    if (!stock || quantity <= 0) return;
    if (riskPct > 5) {
      showToast("Risk too high. Max risk is 5% per trade.", "error");
      return;
    }
    if (!thesisCategory) {
      showToast("Please select a thesis category.", "error");
      return;
    }
    if (thesis.trim().length < 10) {
      showToast("Please enter a detailed thesis.", "error");
      return;
    }

    setAnimating(true);
    
    try {
      // 1. Submit pre-trade journal to backend
      const journalEntry = {
        userId: 1, 
        symbol: stock.symbol,
        thesisCategory,
        thesis,
        entryPrice: price,
        stopLoss: slValue,
        targetPrice: tpValue,
        riskAmount: totalRisk
      };
      
      const res = await fetch('/api/journals/entry', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(journalEntry)
      });
      
      if (!res.ok) {
        throw new Error("Failed to submit journal");
      }
      
      const savedJournal = await res.json();
      
      // 2. Execute local trade (pass journalId to sync on close)
      const result = onTrade({
        symbol: stock.symbol,
        price,
        quantity,
        type: activeType,
        journalId: savedJournal.id
      });

      if (result.success) {
        showToast(result.message, 'success');
        setQuantity(1);
        setThesis('');
        setStopLoss('');
        setTargetPrice('');
      } else {
        showToast(result.message, 'error');
      }
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      setAnimating(false);
    }
  };

  if (!stock) {
    return (
      <div className="trading-panel">
        <div className="trading-panel__header">
          <h3 className="trading-panel__title">Order Ticket</h3>
        </div>
        <div className="trading-panel__empty">
          <p>Select a stock from the watchlist to trade</p>
        </div>
      </div>
    );
  }

  return (
    <div className="trading-panel" style={{ maxHeight: '80vh', overflowY: 'auto' }}>
      {toast && (
        <div className={`trading-toast trading-toast--${toast.type}`}>
          {toast.message}
        </div>
      )}

      <div className="trading-panel__header">
        <h3 className="trading-panel__title">Order & Journal Ticket</h3>
      </div>

      <div className="trading-panel__stock-info">
        <div className="trading-panel__stock-name">
          <span className="trading-panel__symbol">{stock.symbol}</span>
        </div>
        <div className="trading-panel__price-block">
          <span className="trading-panel__current-price">
            ₹{price.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
          </span>
        </div>
      </div>

      <div className="trading-panel__tabs">
        {['BUY', 'SELL', 'SHORT', 'COVER'].map((type) => (
          <button
            key={type}
            className={`trading-panel__tab ${activeType === type ? `trading-panel__tab--${type.toLowerCase()}` : ''}`}
            onClick={() => setActiveType(type)}
          >
            {type}
          </button>
        ))}
      </div>

      <div className="trading-panel__field">
        <label className="trading-panel__label">Quantity</label>
        <input
          type="number"
          min="1"
          value={quantity}
          onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
          className="trading-panel__qty-input"
          style={{ width: '100%', marginBottom: '10px' }}
        />
      </div>

      <div style={{ display: 'flex', gap: '10px', marginBottom: '10px' }}>
        <div className="trading-panel__field" style={{ flex: 1 }}>
          <label className="trading-panel__label">Stop Loss (₹)</label>
          <input
            type="number"
            value={stopLoss}
            onChange={(e) => setStopLoss(e.target.value)}
            className="trading-panel__qty-input"
            style={{ width: '100%' }}
            placeholder="Required"
          />
        </div>
        <div className="trading-panel__field" style={{ flex: 1 }}>
          <label className="trading-panel__label">Target (₹)</label>
          <input
            type="number"
            value={targetPrice}
            onChange={(e) => setTargetPrice(e.target.value)}
            className="trading-panel__qty-input"
            style={{ width: '100%' }}
            placeholder="Required"
          />
        </div>
      </div>

      {/* Risk Engine Warning/Display */}
      <div className="trading-panel__summary" style={{ backgroundColor: riskPct > 5 ? '#ffebee' : '#f5f5f5', color: riskPct > 5 ? '#c62828' : 'inherit' }}>
        <div className="trading-panel__summary-row">
          <span>Account Risk</span>
          <span style={{ fontWeight: riskPct > 5 ? 'bold' : 'normal' }}>
            {riskPct.toFixed(2)}% (Max 5%)
          </span>
        </div>
        <div className="trading-panel__summary-row">
          <span>Risk / Reward</span>
          <span>1 : {riskRewardRatio}</span>
        </div>
        <div className="trading-panel__summary-row trading-panel__summary-row--total">
          <span>Total Trade Cost</span>
          <span>₹{totalCost.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
        </div>
      </div>

      {/* Journal Entry */}
      <div className="trading-panel__field" style={{ marginTop: '15px' }}>
        <label className="trading-panel__label">Pre-Trade Thesis (Required)</label>
        
        <select 
          className="trading-panel__qty-input" 
          style={{ width: '100%', marginBottom: '10px', fontSize: '13px', padding: '6px' }}
          value={thesisCategory}
          onChange={(e) => setThesisCategory(e.target.value)}
        >
          <option value="">-- Select Thesis Category --</option>
          <option value="Breakout">Breakout</option>
          <option value="Support Bounce">Support Bounce</option>
          <option value="Trend Following">Trend Following</option>
          <option value="News Event">News Event</option>
          <option value="Value Investment">Value Investment</option>
          <option value="Other">Other</option>
        </select>

        <textarea
          className="trading-panel__qty-input"
          style={{ width: '100%', minHeight: '80px', padding: '8px', fontSize: '13px' }}
          placeholder="Why are you taking this trade? What is the setup?"
          value={thesis}
          onChange={(e) => setThesis(e.target.value)}
        />
      </div>

      <button
        className={`trading-panel__execute trading-panel__execute--${activeType.toLowerCase()}`}
        onClick={handleTrade}
        disabled={animating || riskPct > 5 || !thesis || !stopLoss || !targetPrice || !thesisCategory}
        style={{ opacity: (riskPct > 5 || !thesis || !stopLoss || !targetPrice || !thesisCategory) ? 0.5 : 1 }}
      >
        {animating ? <span className="trading-panel__spinner" /> : `${activeType} ${quantity} ${stock.symbol}`}
      </button>

      <div className="trading-panel__cash">
        Available Cash: ₹{portfolio.cash.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
      </div>
    </div>
  );
};

export default OrderTicket;
