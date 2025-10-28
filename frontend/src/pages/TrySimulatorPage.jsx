import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import '../styles/theme.css';
import { strategyCatalog } from '../data/strategies';

const API_URL = process.env.REACT_APP_API_URL || 'https://tradelearn-project-production.up.railway.app';

// tiny sample so page runs instantly
const sampleCandles = [
  { date: '2024-01-02', open: 100, high: 102, low: 99, close: 101 },
  { date: '2024-01-03', open: 101, high: 104, low: 100, close: 103 },
  { date: '2024-01-04', open: 103, high: 106, low: 102, close: 105 },
  { date: '2024-01-05', open: 105, high: 106, low: 101, close: 102 },
  { date: '2024-01-08', open: 102, high: 103, low: 99,  close: 100 },
  { date: '2024-01-09', open: 100, high: 101, low: 98,  close: 99 },
  { date: '2024-01-10', open: 99,  high: 100, low: 97,  close: 98 },
  { date: '2024-01-11', open: 98,  high: 100, low: 97,  close: 99 },
  { date: '2024-01-12', open: 99,  high: 101, low: 98,  close: 100 },
  { date: '2024-01-15', open: 100, high: 104, low: 100, close: 103 },
  { date: '2024-01-16', open: 103, high: 106, low: 102, close: 105 },
  { date: '2024-01-23', open: 104, high: 105, low: 101, close: 102 },
];

export default function TrySimulatorPage() {
  const { slug } = useParams();
  const [search] = useSearchParams();
  const strategy = useMemo(
    () => strategyCatalog.find(s => s.slug === slug) || strategyCatalog.find(s => s.slug === 'sma-cross'),
    [slug]
  );

  // parameters state, seeded from defaults or URL overrides
  const [symbol, setSymbol] = useState(search.get('symbol') || 'RELIANCE');
  const [initialCapital, setInitialCapital] = useState(strategy.defaults.initialCapital);
  const [smaFast, setSmaFast] = useState(strategy.defaults.smaFast ?? 5);
  const [smaSlow, setSmaSlow] = useState(strategy.defaults.smaSlow ?? 10);
  const [candles, setCandles] = useState(sampleCandles);

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  // URL overrides (e.g., ?fast=7&slow=21)
  useEffect(() => {
    const fast = Number(search.get('fast'));
    const slow = Number(search.get('slow'));
    const cap = Number(search.get('cap'));
    if (fast) setSmaFast(fast);
    if (slow) setSmaSlow(slow);
    if (cap) setInitialCapital(cap);
  }, [search]);

  const runBacktest = async (e) => {
    e.preventDefault();
    setError('');
    setResult(null);

    if (strategy.comingSoon || strategy.kind !== 'SMA_CROSS') {
      setError('This strategy will be available soon in the simulator.');
      return;
    }

    setLoading(true);
    try {
      const body = {
        symbol,
        initialCapital: Number(initialCapital),
        smaFast: Number(smaFast),
        smaSlow: Number(smaSlow),
        candles: candles.map(c => ({ ...c, open:+c.open, high:+c.high, low:+c.low, close:+c.close })),
      };
      const resp = await axios.post(`${API_URL}/api/strategy/backtest`, body);
      setResult(resp.data);
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Backtest failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container" style={{paddingTop:24,paddingBottom:24}}>
      <h1>{strategy.name}</h1>
      <p style={{color:'var(--muted)'}}>{strategy.summary}</p>

      {/* Teaching panel */}
      <div className="panel" style={{padding:16, marginTop:16}}>
        <h2>How this strategy works</h2>
        <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:16, marginTop:12}}>
          <div>
            <p style={{color:'var(--muted)'}}>Entry rule</p>
            <p>{strategy.rules.entry}</p>
            <p style={{color:'var(--muted)', marginTop:12}}>Exit rule</p>
            <p>{strategy.rules.exit}</p>
          </div>
          <div>
            <p style={{color:'var(--muted)'}}>Pseudocode</p>
            <pre style={{margin:0, background:'#0b1220', padding:12, borderRadius:8, overflow:'auto'}}>
{`for each day:
  if SMA_fast crosses above SMA_slow and not in position:
    buy as many shares as possible
  if SMA_fast crosses below SMA_slow and in position:
    sell all shares
end
equity = cash + shares * close`}
            </pre>
          </div>
        </div>
        {strategy.example?.text && (
          <div style={{marginTop:12}}>
            <p style={{color:'var(--muted)'}}>Example</p>
            <p>{strategy.example.text}</p>
          </div>
        )}
      </div>

      {/* Parameters + Run */}
      <form onSubmit={runBacktest} className="panel" style={{padding:16, marginTop:16}}>
        <h2>Parameters</h2>
        <div style={{display:'grid', gridTemplateColumns:'repeat(4,1fr)', gap:12, marginTop:8}}>
          <div>
            <label>Symbol</label>
            <input className="input" value={symbol} onChange={e=>setSymbol(e.target.value.toUpperCase())} />
          </div>
          <div>
            <label>Initial Capital</label>
            <input className="input" type="number" min="1" value={initialCapital} onChange={e=>setInitialCapital(e.target.value)} />
          </div>
          {strategy.kind === 'SMA_CROSS' && (
            <>
              <div>
                <label>Fast SMA</label>
                <input className="input" type="number" min="1" value={smaFast} onChange={e=>setSmaFast(e.target.value)} />
              </div>
              <div>
                <label>Slow SMA</label>
                <input className="input" type="number" min="2" value={smaSlow} onChange={e=>setSmaSlow(e.target.value)} />
              </div>
            </>
          )}
        </div>

        <div style={{marginTop:12, display:'flex', gap:8}}>
          <button className="btn btn-primary" type="submit" disabled={loading || strategy.comingSoon}>
            {strategy.comingSoon ? 'Coming Soon' : (loading ? 'Running...' : 'Run Backtest')}
          </button>
          <button className="btn btn-outline" type="button" onClick={()=>setCandles(sampleCandles)}>
            Load Sample Data
          </button>
        </div>
      </form>

      {error && <div className="alert alert-danger" style={{marginTop:16}}>{error}</div>}

      {result && (
        <div style={{marginTop:20, display:'grid', gap:16}}>
          <div className="panel" style={{padding:16}}>
            <h2>Summary</h2>
            <div style={{display:'grid', gridTemplateColumns:'repeat(3,1fr)', gap:12, marginTop:12}}>
              <div><p style={{color:'var(--muted)'}}>Initial Capital</p><p>₹{result.initialCapital.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</p></div>
              <div><p style={{color:'var(--muted)'}}>Final Capital</p><p>₹{result.finalCapital.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</p></div>
              <div><p style={{color:'var(--muted)'}}>Return</p><p>{result.returnPct}%</p></div>
              <div><p style={{color:'var(--muted)'}}>Max Drawdown</p><p>{result.maxDrawdownPct}%</p></div>
              <div><p style={{color:'var(--muted)'}}>Win Rate</p><p>{result.winRatePct}%</p></div>
              <div><p style={{color:'var(--muted)'}}>Trades</p><p>{result.tradesCount}</p></div>
            </div>
          </div>

          <div className="panel" style={{padding:16}}>
            <h2>Trades</h2>
            <table className="table" style={{marginTop:10}}>
              <thead><tr><th>Date</th><th>Type</th><th>Price</th><th>Qty</th></tr></thead>
              <tbody>
                {result.trades?.map((t, i) => (
                  <tr key={i}>
                    <td>{t.date}</td><td>{t.type}</td><td>₹{t.price.toFixed(2)}</td><td>{t.quantity}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="panel" style={{padding:16}}>
            <h2>Equity Curve</h2>
            <table className="table" style={{marginTop:10}}>
              <thead><tr><th>Date</th><th>Equity</th></tr></thead>
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
