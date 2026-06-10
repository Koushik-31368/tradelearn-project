import React, { useState, useMemo, useEffect } from 'react';
import StrategyCard from '../components/strategies/StrategyCard';
import StrategyDetail from '../components/strategies/StrategyDetail';
import './StrategiesPage.css';

/* ═══════════════════════════════════════
   STRATEGY DATA (Deep Dive Content)
   ═══════════════════════════════════════ */

const strategiesData = [
  {
    id: 1,
    slug: 'rsi-reversion',
    name: 'RSI Mean Reversion',
    icon: '📈',
    category: 'Technical Analysis',
    risk: 'MEDIUM',
    timeHorizon: '1–5 Days',
    description: 'Buy oversold conditions, sell overbought. Uses the Relative Strength Index to time entries near extremes.',
    whatIs: 'RSI Mean Reversion exploits the tendency of assets to snap back toward their average after extreme moves. When RSI drops below 30, the asset is oversold — a potential buying opportunity. Above 70, it\'s overbought — time to take profits or short.',
    entry: [
      'RSI crosses below 30 on daily chart',
      'Price is near a known support level',
      'No major negative news catalyst',
      'Volume confirms selling exhaustion',
    ],
    exit: [
      'RSI rises above 70 (take profit zone)',
      'Price hits a predefined resistance level',
      'Stop loss triggered below swing low',
      'RSI divergence with price (bearish)',
    ],
    riskRules: [
      'Risk max 1.5% of capital per trade',
      'Stop loss placed below the most recent swing low',
      'Minimum 1:2 risk/reward ratio',
      'Avoid during strong trending markets — RSI stays overbought longer',
    ],
    marketConditions: 'Works best in range-bound, mean-reverting markets where price oscillates between support and resistance. Avoid during strong one-directional trends — RSI can stay at extremes for extended periods.',
    identification: [
      'Daily RSI reading below 30 with price at or near horizontal support',
      'Preceding price decline of 5–10% over 3–5 sessions',
      'Decreasing volume on down days — sellers are exhausted',
      'Bullish divergence: price makes lower low but RSI makes higher low',
    ],
  },
  {
    id: 2,
    slug: 'sma-cross',
    name: 'SMA Crossover',
    icon: '〰️',
    category: 'Trend Following',
    risk: 'MEDIUM',
    timeHorizon: '3–10 Days',
    description: 'Classic trend-following strategy. Buy when fast MA crosses above slow MA, sell on opposite crossover.',
    whatIs: 'The SMA Crossover strategy uses two Simple Moving Averages (typically 9-period and 21-period) to identify trend direction changes. When the faster SMA crosses above the slower one, it signals bullish momentum — and vice versa.',
    entry: [
      '9 SMA crosses above 21 SMA (Golden Cross)',
      'Price is above both moving averages',
      'Volume is above the 20-day average',
      'Confirmation candle closes above the crossover level',
    ],
    exit: [
      '9 SMA crosses below 21 SMA (Death Cross)',
      'Price closes below the slower SMA',
      'Trailing stop hit (2× ATR below entry)',
      'Target reached at next major resistance',
    ],
    riskRules: [
      'Use ATR-based stop losses (1.5–2× ATR)',
      'Max 2% portfolio risk per trade',
      'Reduce size in choppy markets where false crosses occur',
      'Skip signals near major economic events',
    ],
    marketConditions: 'Ideal for trending markets with clear directional momentum. Generates false signals in sideways, choppy conditions. Best applied on daily or 4-hour charts on liquid assets.',
    identification: [
      'Plot 9 SMA and 21 SMA on daily chart',
      'Watch for crossover with price action confirmation',
      'Check ADX > 25 to confirm strength of move',
      'Higher timeframe (weekly) trend should align with the signal direction',
    ],
  },
  {
    id: 3,
    slug: 'breakout',
    name: 'Breakout Trading',
    icon: '💥',
    category: 'Momentum',
    risk: 'HIGH',
    timeHorizon: '1–5 Days',
    description: 'Enter on clean breaks above resistance or below support with volume confirmation. Ride the momentum.',
    whatIs: 'Breakout trading capitalizes on price breaking through established support or resistance levels with strong volume. When a stock consolidates and then breaks out, it often moves rapidly in the breakout direction as stop-losses trigger and new participants enter.',
    entry: [
      'Price closes above resistance with 1.5× average volume',
      'Prior consolidation of at least 5–10 sessions',
      'Clean, horizontal resistance level (not diagonal)',
      'No overhead supply within 3–5% of breakout level',
    ],
    exit: [
      'Price returns below breakout level (failed breakout)',
      'Profit target at measured move (height of range added to breakout)',
      'Trailing stop using 2× ATR',
      'Volume drops significantly after breakout — momentum fading',
    ],
    riskRules: [
      'Stop loss placed just below the breakout level',
      'Risk no more than 1% per trade (breakouts fail often)',
      'Wait for the close above resistance; avoid intraday fakeouts',
      'Size down on first attempt; add on confirmed retest',
    ],
    marketConditions: 'Works best after a period of tight consolidation with decreasing volume (coiling). Ideal during market transitions from range-bound to trending. Avoid in low-liquidity environments.',
    identification: [
      'Identify a clear horizontal resistance with at least 3 prior touches',
      'Volume progressively decreasing during consolidation (coiling action)',
      'Breakout candle: large body, small wick, 1.5×+ average volume',
      'After breakout: healthy pullback to retested level (now support)',
    ],
  },
  {
    id: 4,
    slug: 'momentum-trading',
    name: 'Momentum Trading',
    icon: '🚀',
    category: 'Momentum',
    risk: 'HIGH',
    timeHorizon: 'Hours to Days',
    description: 'Ride stocks with strong directional momentum. Enter on strength, exit before momentum fades.',
    whatIs: 'Momentum trading follows the principle that stocks moving strongly in one direction will continue. Traders identify stocks with outsized volume and price movement, enter in the direction of the move, and exit when momentum signals weaken.',
    entry: [
      'Stock gaps up 3%+ on 2× average volume at open',
      'Relative strength vs. market index is positive',
      'First pullback to VWAP or intraday moving average',
      'Sector/peers also showing strength for confirmation',
    ],
    exit: [
      'First lower high on intraday chart',
      'VWAP break on closing basis',
      'Profit target at prior swing high',
      'End of day — no overnight holds for intraday momentum',
    ],
    riskRules: [
      'Hard stop below VWAP or the first 5-min candle low',
      'Risk 1% max per trade',
      'Scale out: 50% at 1R, 25% at 2R, let rest ride',
      'Avoid momentum trades in the last 30 minutes of session',
    ],
    marketConditions: 'Thrives in strong trending markets or during earnings season when catalysts drive large moves. Performs poorly in choppy, low-volume environments. Best on liquid large and mid-cap stocks.',
    identification: [
      'Pre-market scanner: stocks up 3%+ with 2×+ relative volume',
      'Check for catalyst — earnings, news, sector rotation',
      'Intraday: price above VWAP with ascending 9 EMA',
      'Level 2 shows aggressive buying and large bid prints',
    ],
  },
  {
    id: 5,
    slug: 'support-resistance',
    name: 'Support & Resistance',
    icon: '📊',
    category: 'Swing Trading',
    risk: 'MEDIUM',
    timeHorizon: '2–7 Days',
    description: 'Buy at support levels, sell at resistance. Classic range-trading approach for capturing swings.',
    whatIs: 'Support & Resistance trading identifies key price levels where buying or selling pressure historically concentrates. Traders buy long bounces off support and sell/short rejections from resistance, targeting the opposing level.',
    entry: [
      'Price touches established support with a bullish rejection candle',
      'RSI in oversold zone (< 35) at support',
      'Volume spike at support level (buyers stepping in)',
      'At least 3 prior touches validate the support level',
    ],
    exit: [
      'Price approaches resistance — take profit before',
      'Stop loss below support by 1× ATR',
      'If support breaks with volume: exit immediately',
      'Time stop: exit after 7 sessions if no move materializes',
    ],
    riskRules: [
      'Never risk more than 2% per trade',
      'Wider stops at strong support = smaller position size',
      'Don\'t buy at support without a rejection candle',
      'Avoid trading levels that have been tested too many times (weakened)',
    ],
    marketConditions: 'Ideal for range-bound markets with well-defined horizontal levels. Does not work in strong trending markets where levels get sliced through. Best on daily/4H charts.',
    identification: [
      'Find stocks consolidating between clear horizontal levels',
      'Support: at least 2–3 prior bounces within 1% of the same price',
      'Resistance: at least 2–3 prior rejections',
      'Range width should offer minimum 3:1 R:R from support to resistance',
    ],
  },
  {
    id: 6,
    slug: 'scalping',
    name: 'Scalping',
    icon: '⚡',
    category: 'Day Trading',
    risk: 'HIGH',
    timeHorizon: 'Seconds to Minutes',
    description: 'Ultra-short-term trades targeting tiny price movements. Requires discipline and fast execution.',
    whatIs: 'Scalping extracts small profits repeatedly by entering and exiting positions within seconds to minutes. Scalpers rely on Level 2 order flow, tight spreads, and direct market access. It\'s the most intense trading style.',
    entry: [
      'Bid/ask spread is tight (< 0.05%)',
      'Large bid stack appears at a support level',
      'Price stalls at VWAP with buying pressure',
      'Tape shows aggressive large-lot buying',
    ],
    exit: [
      'Target reached: +0.1% to +0.3% move',
      'Bid support disappears or gets pulled',
      'Price stalls for more than 30 seconds',
      'Hard stop: max loss of 0.1% per trade',
    ],
    riskRules: [
      'Maximum 0.5% risk per trade due to high frequency',
      'Trade only the most liquid instruments',
      'Minimum 2:1 R:R on each scalp',
      'Stop trading after 3 consecutive losses — reset mentality',
    ],
    marketConditions: 'Requires high liquidity and tight spreads. Works in any market condition but best during high-volume sessions (market open, overlap hours). Avoid during low-volume lunch periods.',
    identification: [
      'Select top 5 most liquid names by volume',
      'Watch Level 2 for large resting orders',
      'Use 1-min and tick charts for precise timing',
      'Morning session (first 90 min) offers the best setups',
    ],
  },
  {
    id: 7,
    slug: 'buy-hold',
    name: 'Buy & Hold',
    icon: '💎',
    category: 'Long-term Investing',
    risk: 'LOW',
    timeHorizon: 'Months to Years',
    description: 'Invest in quality companies for the long run. Let compounding and time work in your favor.',
    whatIs: 'Buy & Hold is the simplest, most proven approach. Buy fundamentally strong companies and hold for years. Ignore daily volatility. Warren Buffett\'s favorite strategy — your edge is time and patience.',
    entry: [
      'Strong fundamentals: revenue growth, profit margins, ROE > 15%',
      'Reasonable valuation (P/E below industry average)',
      'Competitive moat: brand, patents, network effects',
      'Management with track record of capital allocation',
    ],
    exit: [
      'Thesis is broken — fundamentals deteriorate materially',
      'Better opportunity with significantly higher risk-adjusted return',
      'Valuation becomes extreme (P/E > 3× historical average)',
      'Capital needed for a life event',
    ],
    riskRules: [
      'Diversify across 15–25 holdings and 5+ sectors',
      'No single position > 8% of portfolio',
      'Dollar-cost average: buy monthly regardless of price',
      'Rebalance annually — trim winners, add to conviction names',
    ],
    marketConditions: 'Works in all market conditions over long horizons. Short-term drawdowns are expected and should be used for additional buying. Compounding requires patience through bear markets.',
    identification: [
      'Screen for companies with 5+ years of revenue growth',
      'Check insider ownership — management should own significant shares',
      'Review free cash flow yield — sustainable dividends and buybacks',
      'Industry tailwinds: growing TAM, secular trends in the sector',
    ],
  },
  {
    id: 8,
    slug: 'macd-strategy',
    name: 'MACD Strategy',
    icon: '📉',
    category: 'Technical Analysis',
    risk: 'MEDIUM',
    timeHorizon: '2–7 Days',
    description: 'Trade MACD histogram crossovers and signal line crosses. A momentum + trend hybrid system.',
    whatIs: 'MACD (Moving Average Convergence Divergence) shows the relationship between two EMAs. When the MACD line crosses above the signal line, momentum is turning bullish. The histogram visualizes the strength of the crossover.',
    entry: [
      'MACD line crosses above signal line',
      'Histogram turns from negative to positive',
      'Price is above the 50 EMA (trend filter)',
      'Volume increases on the crossover session',
    ],
    exit: [
      'MACD line crosses below signal line',
      'Histogram starts shrinking after peak (momentum fading)',
      'Stop loss at the swing low before the signal',
      'Price closes below the 50 EMA',
    ],
    riskRules: [
      'Always have a stop in place (below recent swing low)',
      'Risk 1.5% max per trade',
      'Avoid MACD signals in sideways markets — too many whipsaws',
      'Confirm MACD with at least one other indicator (RSI, volume)',
    ],
    marketConditions: 'Best in trending markets. MACD is a lagging indicator, so it confirms trends after they start. In choppy, sideways markets, MACD produces frequent false signals. Use daily or 4H charts for reliable signals.',
    identification: [
      'Plot MACD (12, 26, 9) on daily chart',
      'Look for bullish divergence: price lower low + MACD higher low',
      'Histogram expansion = increasing momentum',
      'Combine with 50/200 EMA to confirm overall trend direction',
    ],
  },
];

/* ═══════════════════════════════════════
   COMPONENT
   ═══════════════════════════════════════ */

const CATEGORIES = ['All', 'Technical Analysis', 'Trend Following', 'Momentum', 'Swing Trading', 'Day Trading', 'Long-term Investing'];

const StrategiesPage = () => {
  const [selectedStrategy, setSelectedStrategy] = useState(null);
  const [activeCategory, setActiveCategory] = useState('All');

  const filtered = useMemo(() => {
    if (activeCategory === 'All') return strategiesData;
    return strategiesData.filter(s => s.category === activeCategory);
  }, [activeCategory]);

  // Lock body scroll when detail panel is open
  useEffect(() => {
    if (selectedStrategy) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => { document.body.style.overflow = ''; };
  }, [selectedStrategy]);

  return (
    <div className="strategies-page">
      {/* Hero */}
      <header className="strat-hero">
        <div className="strat-hero__inner">
          <span className="strat-hero__tag">Strategy Library</span>
          <h1 className="strat-hero__heading">
            Proven Trading Strategies.<br />
            <span className="strat-hero__accent">Detailed. Actionable. Tested.</span>
          </h1>
          <p className="strat-hero__sub">
            Each strategy comes with entry/exit rules, risk parameters, market conditions,
            real chart examples, and a direct link to the simulator.
          </p>
          <div className="strat-hero__stats">
            <div className="strat-hero__stat">
              <span className="strat-hero__stat-value">{strategiesData.length}</span>
              <span className="strat-hero__stat-label">Strategies</span>
            </div>
            <div className="strat-hero__stat-divider" />
            <div className="strat-hero__stat">
              <span className="strat-hero__stat-value">7</span>
              <span className="strat-hero__stat-label">Sections Each</span>
            </div>
            <div className="strat-hero__stat-divider" />
            <div className="strat-hero__stat">
              <span className="strat-hero__stat-value">∞</span>
              <span className="strat-hero__stat-label">Simulator Runs</span>
            </div>
          </div>
        </div>
      </header>

      {/* Journey Breadcrumb */}
      <div className="strat-journey">
        <div className="strat-journey__inner">
          <span className="strat-journey__step">Learn</span>
          <span className="strat-journey__arrow">→</span>
          <span className="strat-journey__step strat-journey__step--active">Strategies</span>
          <span className="strat-journey__arrow">→</span>
          <span className="strat-journey__step">Simulator</span>
          <span className="strat-journey__arrow">→</span>
          <span className="strat-journey__step">Multiplayer</span>
        </div>
      </div>

      {/* Main */}
      <main className="strat-main">
        {/* Category Filter */}
        <div className="strat-filters">
          {CATEGORIES.map(cat => (
            <button
              key={cat}
              className={`strat-filter${activeCategory === cat ? ' strat-filter--active' : ''}`}
              onClick={() => setActiveCategory(cat)}
            >
              {cat}
            </button>
          ))}
        </div>

        {/* Cards Grid */}
        <div className="strat-grid">
          {filtered.map(strategy => (
            <StrategyCard
              key={strategy.id}
              strategy={strategy}
              onClick={setSelectedStrategy}
            />
          ))}
        </div>

        {filtered.length === 0 && (
          <div className="strat-empty">
            No strategies found in this category.
          </div>
        )}
      </main>

      {/* Detail Overlay */}
      {selectedStrategy && (
        <StrategyDetail
          strategy={selectedStrategy}
          onClose={() => setSelectedStrategy(null)}
        />
      )}
    </div>
  );
};

export default StrategiesPage;