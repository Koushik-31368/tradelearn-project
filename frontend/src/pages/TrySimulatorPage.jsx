import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useSearchParams, Link } from 'react-router-dom';
import axios from 'axios';
import '../styles/theme.css';
import { strategyCatalog } from '../data/strategies';

const API_URL = process.env.REACT_APP_API_URL || 'https://tradelearn-project-production.up.railway.app';

// Tiny sample data so the page runs instantly
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

// Helper to create a dynamic initial state for parameters
const getInitialParams = (strategy, search) => {
  const params = { ...strategy.defaults };
  for (const p of strategy.params) {
    const urlValue = search.get(p.key);
    if (urlValue) {
      params[p.key] = p.type === 'number' ? Number(urlValue) : urlValue;
    }
  }
  return params;
};

export default function TrySimulatorPage() {
  const { slug } = useParams();
  const [search, setSearch] = useSearchParams();
  const strategy = useMemo(
    () => strategyCatalog.find(s => s.slug === slug) || strategyCatalog.find(s => s.slug === 'sma-cross'),
    [slug]
  );

  const [symbol, setSymbol] = useState(search.get('symbol') || 'RELIANCE');
  const [params, setParams] = useState(getInitialParams(strategy, search));
  const [candles, setCandles] = useState(sampleCandles);

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  // When strategy changes, reset params
  useEffect(() => {
    setParams(getInitialParams(strategy, search));
    setResult(null);
    setError('');
  }, [strategy, search]);

  const handleParamChange = (key, value, type) => {
    setParams(prev => ({ ...prev, [key]: type === 'number' ? Number(value) : value }));
  };

  const runBacktest = async (e) => {
    e.preventDefault();
    setError('');
    setResult(null);

    if (strategy.comingSoon) {
      setError('This strategy simulator is coming soon. Try another one!');
      return;
    }

    setLoading(true);
    try {
      const body = {
        strategy: {
          kind: strategy.kind,
          params: params,
        },
        symbol,
        initialCapital: Number(params.initialCapital),
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
    <div className="container" style={{paddingTop:24,paddingBottom:24, maxWidth: 900}}>
      <h1 style={{fontSize: '2.5rem'}}>{strategy.name}</h1>
      <p style={{fontSize: '1.2rem', color:'var(--muted)'}}>{strategy.tagline}</p>
      <p style={{marginTop: 16, lineHeight: 1.6}}>{strategy.hero}</p>

      {/* Core Concept */}
      <div className="panel" style={{padding:16, marginTop:24}}>
        <h2>Core Concept</h2>
        <p style={{marginTop: 8}}>{strategy.concept.simple}</p>
        <p style={{marginTop: 12, color: 'var(--muted-dark)'}}>{strategy.concept.why}</p>
      </div>

      {/* Rules */}
      <div className="panel" style={{padding:16, marginTop:16}}>
        <h2>Rules</h2>
        <div style={{display:'grid', gridTemplateColumns:'1fr 1fr 1fr', gap:16, marginTop:12}}>
          <div><p style={{color:'var(--muted)'}}>Entry</p><p>{strategy.rules.entry}</p></div>
          <div><p style={{color:'var(--muted)'}}>Exit</p><p>{strategy.rules.exit}</p></div>
          <div><p style={{color:'var(--muted)'}}>Position Sizing</p><p>{strategy.rules.position}</p></div>
        </div>
      </div>

      {/* Scenarios */}
      <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:16, marginTop:16}}>
        <div className="panel" style={{padding:16}}>
          <h3 style={{color:'var(--success)'}}>{strategy.bestFor.title}</h3>
          {strategy.bestFor.scenarios.map((s,i) => (
            <div key={i} style={{marginTop:12}}>
              <p><strong>{s.label}</strong></p>
              <p style={{fontSize:'.9rem', color:'var(--muted)'}}>{s.examples}</p>
              <p style={{fontSize:'.9rem', marginTop:4}}>{s.why}</p>
            </div>
          ))}
        </div>
        <div className="panel" style={{padding:16}}>
          <h3 style={{color:'var(--danger)'}}>{strategy.avoidFor.title}</h3>
          {strategy.avoidFor.scenarios.map((s,i) => (
            <div key={i} style={{marginTop:12}}>
              <p><strong>{s.label}</strong></p>
              <p style={{fontSize:'.9rem', color:'var(--muted)'}}>{s.examples}</p>
              <p style={{fontSize:'.9rem', marginTop:4}}>{s.why}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Interactive Demo Links */}
      <div className="panel" style={{padding:16, marginTop:16}}>
        <h3>Interactive Demo</h3>
        <p style={{color:'var(--muted)'}}>{strategy.interactiveDemo.setup}</p>
        <div style={{display:'flex', gap:8, marginTop:12, flexWrap:'wrap'}}>
          {strategy.interactiveDemo.scenarios.map((s,i) => {
            const urlParams = new URLSearchParams({
              symbol: s.symbol,
              ...s.params
            });
            return <Link key={i} to={`?${urlParams}`} className="btn btn-outline">{s.label}</Link>
          })}
        </div>
      </div>

      {/* Parameters + Run */}
      <form onSubmit={runBacktest} className="panel" style={{padding:16, marginTop:24}}>
        <h2>Try It Live</h2>
        <div style={{display:'grid', gridTemplateColumns:'repeat(auto-fit, minmax(150px, 1fr))', gap:12, marginTop:8}}>
          <div>
            <label>Symbol</label>
            <input className="input" value={symbol} onChange={e=>setSymbol(e.target.value.toUpperCase())} />
          </div>
          {strategy.params.map(p => (
            <div key={p.key}>
              <label>{p.label}</label>
              <input
                className="input"
                type={p.type}
                min={p.min}
                max={p.max}
                value={params[p.key]}
                onChange={e => handleParamChange(p.key, e.target.value, p.type)}
                title={p.hint}
              />
            </div>
          ))}
        </div>

        <div style={{marginTop:16, display:'flex', gap:8}}>
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
            {result.trades?.length > 0 ? (
              <table className="table" style={{marginTop:10}}>
                <thead><tr><th>Date</th><th>Type</th><th>Price</th><th>Qty</th></tr></thead>
                <tbody>
                  {result.trades.map((t, i) => (
                    <tr key={i}>
                      <td>{t.date}</td><td>{t.type}</td><td>₹{t.price.toFixed(2)}</td><td>{t.quantity}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : <p className="no-history">No trades were executed.</p>}
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

      {/* Pro Tips */}
      <div className="panel" style={{padding:16, marginTop:24}}>
        <h3>Pro Tips</h3>
        <ul style={{margin:0, paddingLeft:20, marginTop:12}}>
          {strategy.proTips.map((tip, i) => <li key={i} style={{marginBottom:8}}>{tip}</li>)}
        </ul>
      </div>
    </div>
  );
}
