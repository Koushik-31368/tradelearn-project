// src/pages/TrySimulatorPage.jsx
import React, { useState } from 'react';
import axios from 'axios';
import '../styles/theme.css';

const API_URL = process.env.REACT_APP_API_URL || 'https://tradelearn-project-production.up.railway.app';

// Lightweight sample candles so the page works instantly.
// Replace with real OHLCV later (ISO dates required).
const sampleCandles = [
  { date: '2024-01-01', open: 100, high: 101, low: 99, close: 100 },
  { date: '2024-01-02', open: 100, high: 102, low: 99, close: 101 },
  { date: '2024-01-03', open: 101, high: 103, low: 100, close: 102 },
  { date: '2024-01-04', open: 102, high: 104, low: 101, close: 103 },
  { date: '2024-01-05', open: 103, high: 105, low: 102, close: 102 },
  { date: '2024-01-08', open: 102, high: 103, low: 99,  close: 100 },
  { date: '2024-01-09', open: 100, high: 101, low: 98,  close: 99 },
  { date: '2024-01-10', open: 99,  high: 100, low: 97,  close: 98 },
  { date: '2024-01-11', open: 98,  high: 100, low: 97,  close: 99 },
  { date: '2024-01-12', open: 99,  high: 101, low: 98,  close: 100 },
  { date: '2024-01-15', open: 100, high: 103, low: 100, close: 102 },
  { date: '2024-01-16', open: 102, high: 104, low: 101, close: 103 },
  { date: '2024-01-17', open: 103, high: 106, low: 103, close: 105 },
  { date: '2024-01-18', open: 105, high: 107, low: 104, close: 106 },
  { date: '2024-01-19', open: 106, high: 108, low: 105, close: 107 },
  { date: '2024-01-22', open: 107, high: 108, low: 105, close: 106 },
  { date: '2024-01-23', open: 106, high: 107, low: 103, close: 104 },
  { date: '2024-01-24', open: 104, high: 105, low: 101, close: 102 },
  { date: '2024-01-25', open: 102, high: 103, low: 100, close: 101 },
  { date: '2024-01-26', open: 101, high: 102, low: 99,  close: 100 },
];

export default function TrySimulatorPage() {
  const [symbol, setSymbol] = useState('RELIANCE');
  const [initialCapital, setInitialCapital] = useState(100000);
  const [smaFast, setSmaFast] = useState(5);
  const [smaSlow, setSmaSlow] = useState(10);
  const [candles, setCandles] = useState(sampleCandles);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  const runBacktest = async (e) => {
    e.preventDefault();
    setError('');
    setResult(null);
    setLoading(true);
    try {
      const body = {
        symbol,
        initialCapital: Number(initialCapital),
        smaFast: Number(smaFast),
        smaSlow: Number(smaSlow),
        candles: candles.map(c => ({
          date: c.date,
          open: Number(c.open),
          high: Number(c.high),
          low: Number(c.low),
          close: Number(c.close),
        })),
      };
      const resp = await axios.post(`${API_URL}/api/strategy/backtest`, body);
      setResult(resp.data);
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Backtest failed');
    } finally {
      setLoading(false);
    }
  };

  const loadSample = () => setCandles(sampleCandles);

  return (
    <div className="container" style={{paddingTop: 24, paddingBottom: 24}}>
      <h1>Strategy Simulator</h1>
      <p style={{color: 'var(--muted)'}}>Run a simple SMA crossover backtest on your OHLC candles.</p>

      <form onSubmit={runBacktest} className="panel" style={{padding: 16, marginTop: 16}}>
        <div style={{display:'grid', gridTemplateColumns:'repeat(4, 1fr)', gap: 12}}>
          <div>
            <label>Symbol</label>
            <input className="input" value={symbol} onChange={e=>setSymbol(e.target.value)} />
          </div>
          <div>
            <label>Initial Capital</label>
            <input className="input" type="number" min="1" value={initialCapital} onChange={e=>setInitialCapital(e.target.value)} />
          </div>
          <div>
            <label>Fast SMA</label>
            <input className="input" type="number" min="1" value={smaFast} onChange={e=>setSmaFast(e.target.value)} />
          </div>
          <div>
            <label>Slow SMA</label>
            <input className="input" type="number" min="2" value={smaSlow} onChange={e=>setSmaSlow(e.target.value)} />
          </div>
        </div>

        <div style={{marginTop: 12, display:'flex', gap: 8}}>
          <button className="btn btn-primary" type="submit" disabled={loading}>
            {loading ? 'Running...' : 'Run Backtest'}
          </button>
          <button className="btn btn-outline" type="button" onClick={loadSample}>
            Load Sample Data
          </button>
        </div>
      </form>

      {error && <div className="alert alert-danger" style={{marginTop: 16}}>{error}</div>}

      {result && (
        <div style={{marginTop: 20, display:'grid', gap: 16}}>
          <div className="panel" style={{padding: 16}}>
            <h2>Summary</h2>
            <div style={{display:'grid', gridTemplateColumns:'repeat(3, 1fr)', gap: 12, marginTop: 12}}>
              <div>
                <p style={{color:'var(--muted)'}}>Initial Capital</p>
                <p>₹{result.initialCapital.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</p>
              </div>
              <div>
                <p style={{color:'var(--muted)'}}>Final Capital</p>
                <p>₹{result.finalCapital.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</p>
              </div>
              <div>
                <p style={{color:'var(--muted)'}}>Return</p>
                <p>{result.returnPct}%</p>
              </div>
              <div>
                <p style={{color:'var(--muted)'}}>Max Drawdown</p>
                <p>{result.maxDrawdownPct}%</p>
              </div>
              <div>
                <p style={{color:'var(--muted)'}}>Win Rate</p>
                <p>{result.winRatePct}%</p>
              </div>
              <div>
                <p style={{color:'var(--muted)'}}>Trades</p>
                <p>{result.tradesCount}</p>
              </div>
            </div>
          </div>

          <div className="panel" style={{padding: 16}}>
            <h2>Trades</h2>
            <table className="table" style={{marginTop: 10}}>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Type</th>
                  <th>Price</th>
                  <th>Qty</th>
                </tr>
              </thead>
              <tbody>
                {result.trades?.map((t, i) => (
                  <tr key={i}>
                    <td>{t.date}</td>
                    <td>{t.type}</td>
                    <td>₹{t.price.toFixed(2)}</td>
                    <td>{t.quantity}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="panel" style={{padding: 16}}>
            <h2>Equity Curve</h2>
            <table className="table" style={{marginTop: 10}}>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Equity</th>
                </tr>
              </thead>
              <tbody>
                {result.equityCurve?.map((p, i) => (
                  <tr key={i}>
                    <td>{p.date}</td>
                    <td>₹{p.equity.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}