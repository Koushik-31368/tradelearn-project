import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useSearchParams, Link } from 'react-router-dom';
import axios from 'axios';
import '../styles/theme.css';
import { strategyCatalog } from '../data/strategies';

const API_URL =
  process.env.REACT_APP_API_URL ||
  'https://tradelearn-project-1.onrender.com';

// Tiny sample data
const sampleCandles = [
  { date: '2024-01-02', open: 100, high: 102, low: 99, close: 101 },
  { date: '2024-01-03', open: 101, high: 104, low: 100, close: 103 },
  { date: '2024-01-04', open: 103, high: 106, low: 102, close: 105 },
  { date: '2024-01-05', open: 105, high: 106, low: 101, close: 102 },
];

const getInitialParams = (strategy, search) => {
  const params = { ...strategy.defaults };
  for (const p of strategy.params) {
    const v = search.get(p.key);
    if (v) params[p.key] = p.type === 'number' ? Number(v) : v;
  }
  return params;
};

export default function TrySimulatorPage() {
  const { slug } = useParams();
  const [search] = useSearchParams(); // ✅ FIXED (no setSearch)

  const strategy = useMemo(
    () =>
      strategyCatalog.find(s => s.slug === slug) ||
      strategyCatalog[0],
    [slug]
  );

  const [symbol, setSymbol] = useState(search.get('symbol') || 'RELIANCE');
  const [params, setParams] = useState(getInitialParams(strategy, search));
  const [candles, setCandles] = useState(sampleCandles);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    setParams(getInitialParams(strategy, search));
    setResult(null);
    setError('');
  }, [strategy, search]);

  const runBacktest = async (e) => {
    e.preventDefault();

    if (strategy.comingSoon) {
      setError('This strategy is coming soon.');
      return;
    }

    setLoading(true);
    setError('');
    setResult(null);

    try {
      const body = {
        strategy: {
          kind: strategy.kind,
          params,
        },
        symbol,
        initialCapital: Number(params.initialCapital),
        candles,
      };

      const res = await axios.post(
        `${API_URL}/api/strategy/backtest`,
        body
      );

      setResult(res.data);
    } catch (err) {
      setError(err?.response?.data?.message || 'Backtest failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container" style={{ maxWidth: 900 }}>
      <h1>{strategy.name}</h1>
      <p>{strategy.tagline}</p>

      <form onSubmit={runBacktest}>
        <input
          value={symbol}
          onChange={e => setSymbol(e.target.value.toUpperCase())}
        />

        {strategy.params.map(p => (
          <input
            key={p.key}
            type={p.type}
            value={params[p.key]}
            onChange={e =>
              setParams(prev => ({
                ...prev,
                [p.key]:
                  p.type === 'number'
                    ? Number(e.target.value)
                    : e.target.value,
              }))
            }
          />
        ))}

        <button type="submit" disabled={loading}>
          {loading ? 'Running...' : 'Run Backtest'}
        </button>
      </form>

      {error && <p style={{ color: 'red' }}>{error}</p>}
      {result && <pre>{JSON.stringify(result, null, 2)}</pre>}
    </div>
  );
}
