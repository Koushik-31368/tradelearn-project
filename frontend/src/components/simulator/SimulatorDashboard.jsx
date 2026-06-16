// src/components/simulator/SimulatorDashboard.jsx
// Main simulator dashboard — orchestrates all sub-components.
import React, { useState, useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import PortfolioSummary from './PortfolioSummary';
import Watchlist from './Watchlist';
import CandlestickChart from './CandlestickChart';
import OrderTicket from './OrderTicket';
import AnalyticsDashboard from './AnalyticsDashboard';
import TransactionHistory from './TransactionHistory';
import MarketSentiment from './MarketSentiment';
import ReflectionModal from './ReflectionModal';
import ReadinessDashboard from './ReadinessDashboard';
import {
  getDailyStocks,
  generateEquityCurve,
  computeSMA,
  getPortfolio,
  getTradeHistory,
  executeDemoTrade,
} from '../../utils/simulatorData';
import { generateInitialCandles } from '../../utils/marketSimulator';
import './SimulatorDashboard.css';

/* ── Strategy slug → display name mapping ── */
const STRATEGY_NAMES = {
  'rsi-reversion': 'RSI Mean Reversion',
  'sma-cross': 'SMA Crossover',
  'breakout': 'Breakout Trading',
  'momentum-trading': 'Momentum Trading',
  'support-resistance': 'Support & Resistance',
  'scalping': 'Scalping',
  'buy-hold': 'Buy & Hold',
  'macd-strategy': 'MACD Strategy',
};

const SimulatorDashboard = () => {
  const [searchParams] = useSearchParams();
  const strategySlug = searchParams.get('strategy');
  const strategyName = strategySlug ? STRATEGY_NAMES[strategySlug] || strategySlug : null;

  const [stocks] = useState(() => getDailyStocks());
  const [selectedSymbol, setSelectedSymbol] = useState('RELIANCE');
  const [portfolio, setPortfolio] = useState(() => getPortfolio());
  const [trades, setTrades] = useState(() => getTradeHistory());
  const [equityCurve, setEquityCurve] = useState(() => generateEquityCurve());
  const [tick, setTick] = useState(0); // For forcing re-renders
  const [activeTab, setActiveTab] = useState('analytics'); // 'analytics' or 'readiness'

  const [candles, setCandles] = useState([]);
  const [visibleCount, setVisibleCount] = useState(0);

  // Load historical data for selected symbol (Default to 2020 crash scenario as a test)
  React.useEffect(() => {
    import('../../services/marketApi').then(({ fetchMarketHistory }) => {
      // Hardcode COVID crash dates for now until Scenario UI is built in Dashboard
      fetchMarketHistory(selectedSymbol, '2020-02-01', '2020-04-30').then(data => {
        setCandles(data);
        setVisibleCount(10); // show first 10 candles
      }).catch(err => console.error("Failed to load historical data", err));
    });
  }, [selectedSymbol]);

  const equityCurve = useMemo(() => generateEquityCurve(), []);

  const selectedStock = useMemo(
    () => stocks.find((s) => s.symbol === selectedSymbol) || { symbol: selectedSymbol, price: 0 },
    [stocks, selectedSymbol]
  );

  const visibleCandles = useMemo(() => candles.slice(0, visibleCount), [candles, visibleCount]);
  const smaData = useMemo(() => (visibleCandles.length > 0 ? computeSMA(visibleCandles, 7) : []), [visibleCandles]);

  const handleSelect = useCallback((symbol) => {
    setSelectedSymbol(symbol);
  }, []);

  const handleTrade = useCallback(
    (tradeParams) => {
      const result = executeDemoTrade(tradeParams);
      if (result.success) {
        setPortfolio(result.portfolio);
        setTrades(getTradeHistory());
        setTick((t) => t + 1);
      }
      return result;
    },
    []
  );

  return (
    <div className="sim-dashboard">
      {/* Post-Trade Reflection Modal (Blocks UI if there are pending reflections) */}
      <ReflectionModal userId={1} onReflectionsComplete={() => setTick((t) => t + 1)} />

      {/* Strategy Mode Banner */}
      {strategyName && (
        <div className="sim-dashboard__strategy-banner">
          <span className="sim-dashboard__strategy-icon">🎯</span>
          <span className="sim-dashboard__strategy-label">
            Strategy Mode: <strong>{strategyName}</strong>
          </span>
          <span className="sim-dashboard__strategy-hint">
            Practice this strategy with simulated trades below
          </span>
        </div>
      )}

      {/* Top — Portfolio Summary Cards */}
      <PortfolioSummary portfolio={portfolio} stocks={stocks} equityCurve={equityCurve} />

      {/* Main 3-Column Layout */}
      <div className="sim-dashboard__main">
        {/* Left — Watchlist */}
        <aside className="sim-dashboard__left">
          <Watchlist stocks={stocks} selectedSymbol={selectedSymbol} onSelect={handleSelect} />
        </aside>

        {/* Center — Chart */}
        <section className="sim-dashboard__center">
          <CandlestickChart
            candles={visibleCandles}
            smaData={smaData}
            symbol={selectedSymbol}
            basePrice={selectedStock?.price}
            liveMode={true}
          />
        </section>

        {/* Right — Sentiment + Order Ticket */}
        <aside className="sim-dashboard__right">
          <MarketSentiment candles={visibleCandles} />
          <OrderTicket stock={selectedStock} portfolio={portfolio} onTrade={handleTrade} />
        </aside>
      </div>

      {/* Bottom — Analytics / Readiness + History */}
      <div className="sim-dashboard__bottom" style={{ display: 'block' }}>
        <div style={{ marginBottom: '15px', display: 'flex', gap: '10px' }}>
          <button 
            style={{ padding: '8px 16px', backgroundColor: activeTab === 'analytics' ? '#21262d' : 'transparent', color: activeTab === 'analytics' ? '#c9d1d9' : '#8b949e', border: '1px solid #30363d', borderRadius: '6px', cursor: 'pointer' }}
            onClick={() => setActiveTab('analytics')}
          >
            Performance Analytics
          </button>
          <button 
            style={{ padding: '8px 16px', backgroundColor: activeTab === 'readiness' ? '#21262d' : 'transparent', color: activeTab === 'readiness' ? '#d2a8ff' : '#8b949e', border: '1px solid #30363d', borderRadius: '6px', cursor: 'pointer' }}
            onClick={() => setActiveTab('readiness')}
          >
            Readiness Coaching
          </button>
        </div>

        {activeTab === 'analytics' ? (
          <AnalyticsDashboard userId={1} />
        ) : (
          <ReadinessDashboard userId={1} />
        )}
        
        <div style={{ marginTop: '20px' }}>
          <TransactionHistory trades={trades} />
        </div>
      </div>
    </div>
  );
};

export default SimulatorDashboard;
