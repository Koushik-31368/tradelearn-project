import React from 'react';
import { useNavigate } from 'react-router-dom';
import LearnSection from '../components/learn/LearnSection';
import LearnCard from '../components/learn/LearnCard';
import QuizCard from '../components/learn/QuizCard';
import CandleDiagram from '../components/learn/CandleDiagram';
import './LearnPage.css';

/* ─────────── SECTION 1: BASICS ─────────── */

const basicsCards = [
  {
    icon: '📊',
    title: 'What is a Stock?',
    description:
      'A stock is a fractional ownership unit in a company. Holding shares entitles you to a portion of profits, dividends, and voting rights.',
  },
  {
    icon: '🏛️',
    title: 'What is the Stock Market?',
    description:
      'A regulated marketplace where buyers and sellers transact shares of publicly listed companies through exchanges like NYSE, NASDAQ, NSE & BSE.',
  },
  {
    icon: '📈',
    title: 'How Prices Move',
    description:
      'Prices move based on supply and demand. More buyers push prices up; more sellers push prices down. Every trade requires a counterparty.',
  },
  {
    icon: '🕯️',
    title: 'Candlesticks (OHLC)',
    description:
      'Each candlestick encodes four data points — Open, High, Low, Close — representing complete price action within a specific time period.',
    highlight: true,
  },
  {
    icon: '📉',
    title: 'Volume',
    description:
      'Volume measures how many shares were traded in a period. High volume confirms trend strength; low volume signals indecision or exhaustion.',
  },
  {
    icon: '⬆️',
    title: 'Long vs Short',
    description:
      'Going long means buying expecting price to rise. Going short means selling borrowed shares expecting a decline, then buying back cheaper.',
  },
];

/* ─────────── SECTION 2: REAL WORLD MARKET ─────────── */

const realWorldCards = [
  {
    icon: '🏦',
    title: 'Interest Rates',
    description:
      'Central banks set benchmark rates. Rising rates increase borrowing costs, compress valuations, and shift capital from equities to bonds.',
  },
  {
    icon: '💸',
    title: 'Inflation',
    description:
      'Persistent price increases erode purchasing power. High inflation forces central banks to tighten policy, which pressures growth stocks.',
  },
  {
    icon: '🐂',
    title: 'Bull vs Bear Markets',
    description:
      'A bull market sees prices rising 20%+ from lows with optimistic sentiment. A bear market is a 20%+ decline driven by fear and capitulation.',
  },
  {
    icon: '🏢',
    title: 'Institutional Investors',
    description:
      'Hedge funds, mutual funds, and pension funds move massive volumes. Their accumulation and distribution phases create multi-week trends.',
  },
  {
    icon: '⚡',
    title: 'Global Events Impact',
    description:
      'Wars, elections, trade policies, and pandemics create volatility spikes. Markets price in risk — uncertainty drives sharper moves than outcomes.',
  },
];

/* ─────────── SECTION 3: ADVANCED CONCEPTS ─────────── */

const advancedCards = [
  {
    icon: '🛡️',
    title: 'Risk Management',
    description:
      'The core discipline of profitable trading. Never risk more than 1–2% of capital per trade. Survival is the prerequisite to long-term profit.',
    highlight: true,
  },
  {
    icon: '🚫',
    title: 'Stop Loss',
    description:
      'A pre-defined exit price that caps your downside. Place stops at logical levels — below support for longs, above resistance for shorts.',
  },
  {
    icon: '⚖️',
    title: 'Risk/Reward Ratio',
    description:
      'Compare potential loss vs. potential gain before every trade. A 1:3 R:R means risking ₹1 to make ₹3. Consistent asymmetry compounds edge.',
  },
  {
    icon: '📉',
    title: 'Drawdown',
    description:
      'Peak-to-trough decline in your portfolio. A 50% drawdown requires a 100% gain to recover. Keeping drawdowns under 15% is professional-grade.',
  },
  {
    icon: '📐',
    title: 'Position Sizing',
    description:
      'Calculate lot size based on stop distance and risk tolerance. Proper sizing ensures no single loss can materially damage your account.',
  },
  {
    icon: '🧱',
    title: 'Market Structure',
    description:
      'Markets move in Wyckoff cycles: accumulation → markup → distribution → markdown. Recognizing structure helps time entries and avoid traps.',
  },
];

/* ─────────── SECTION 4: TRADING PSYCHOLOGY ─────────── */

const psychologyCards = [
  {
    icon: '😨',
    title: 'Fear',
    description:
      'Fear causes premature exits and missed entries. The fix: predefined rules, mechanical execution. If your plan says enter, you enter.',
    highlight: true,
  },
  {
    icon: '🤑',
    title: 'Greed',
    description:
      'Greed leads to oversized positions and moved stop-losses. Set profit targets before entering. Let the system decide, not emotions.',
  },
  {
    icon: '🔄',
    title: 'Overtrading',
    description:
      'Taking too many trades dilutes edge and racks up fees. Quality over quantity. Wait for A+ setups that match your system\'s criteria.',
  },
  {
    icon: '📱',
    title: 'FOMO',
    description:
      'Fear of missing out leads to chasing extended moves. If you missed the entry, you missed it. The market offers new setups every day.',
  },
  {
    icon: '🎯',
    title: 'Discipline',
    description:
      'The single trait that separates profitable traders from the rest. Follow your plan, journal every trade, and review weekly. No shortcuts.',
  },
];

/* ─────────── QUIZZES ─────────── */

const quizBasics = {
  question: 'A candlestick with a long lower wick and small body near the top is called:',
  options: ['Shooting Star', 'Doji', 'Hammer', 'Engulfing'],
  correctIndex: 2,
  explanation:
    'A Hammer forms when sellers push prices sharply lower, but buyers recover the loss by close — signaling potential bullish reversal.',
};

const quizRealWorld = {
  question: 'When a central bank raises interest rates, what typically happens to stock valuations?',
  options: [
    'They increase because borrowing is encouraged',
    'They decrease because future earnings are discounted more',
    'They stay the same',
    'Only small-cap stocks are affected',
  ],
  correctIndex: 1,
  explanation:
    'Higher rates raise the discount rate on future cash flows, reducing the present value of earnings and compressing P/E ratios across equities.',
};

const quizAdvanced = {
  question: 'If your portfolio drops by 50%, what gain is needed to recover to breakeven?',
  options: ['50%', '75%', '100%', '150%'],
  correctIndex: 2,
  explanation:
    'From ₹100 to ₹50 is a 50% loss. From ₹50 back to ₹100 requires a 100% gain. This asymmetry is why capital preservation comes first.',
};

const quizPsychology = {
  question: 'A trader keeps moving their stop-loss further away to avoid being stopped out. This is primarily driven by:',
  options: ['Risk management', 'Greed', 'Fear of loss', 'Discipline'],
  correctIndex: 2,
  explanation:
    'Moving stops to avoid losses is fear-driven behavior. It defeats the purpose of risk management and can turn small losses into account-damaging ones.',
};

/* ─────────── COMPONENT ─────────── */

const LearnPage = () => {
  const navigate = useNavigate();

  const renderCards = (cards) => (
    <div className="learn-cards-grid">
      {cards.map((card, i) => (
        <LearnCard key={i} {...card} />
      ))}
    </div>
  );

  return (
    <div className="learn-page">
      {/* Hero */}
      <header className="learn-hero">
        <div className="learn-hero__inner">
          <span className="learn-hero__tag">Trading Academy</span>
          <h1 className="learn-hero__heading">
            Build Your Foundation.<br />
            <span className="learn-hero__accent">Understand the Markets.</span>
          </h1>
          <p className="learn-hero__sub">
            Master the core concepts every trader needs before deploying capital.
            No strategies here — just the knowledge that makes strategies work.
          </p>
          <div className="learn-hero__stats">
            <div className="learn-hero__stat">
              <span className="learn-hero__stat-value">4</span>
              <span className="learn-hero__stat-label">Modules</span>
            </div>
            <div className="learn-hero__stat-divider" />
            <div className="learn-hero__stat">
              <span className="learn-hero__stat-value">22</span>
              <span className="learn-hero__stat-label">Topics</span>
            </div>
            <div className="learn-hero__stat-divider" />
            <div className="learn-hero__stat">
              <span className="learn-hero__stat-value">4</span>
              <span className="learn-hero__stat-label">Quizzes</span>
            </div>
          </div>
        </div>
      </header>

      {/* Journey Breadcrumb */}
      <div className="learn-journey">
        <div className="learn-journey__inner">
          <span className="learn-journey__step learn-journey__step--active">Learn</span>
          <span className="learn-journey__arrow">→</span>
          <span className="learn-journey__step">Strategies</span>
          <span className="learn-journey__arrow">→</span>
          <span className="learn-journey__step">Simulator</span>
          <span className="learn-journey__arrow">→</span>
          <span className="learn-journey__step">Multiplayer</span>
        </div>
      </div>

      {/* Main Content */}
      <main className="learn-main">
        {/* Section 1: Basics */}
        <LearnSection id="basics" number={1} title="Basics of Trading" icon="📘" defaultOpen>
          {renderCards(basicsCards)}
          <CandleDiagram />
          <QuizCard {...quizBasics} />
        </LearnSection>

        {/* Section 2: Real World Market */}
        <LearnSection id="real-world" number={2} title="Real World Market" icon="🌐">
          {renderCards(realWorldCards)}
          <QuizCard {...quizRealWorld} />
        </LearnSection>

        {/* Section 3: Advanced Concepts */}
        <LearnSection id="advanced" number={3} title="Advanced Concepts" icon="🎯">
          {renderCards(advancedCards)}
          <QuizCard {...quizAdvanced} />
        </LearnSection>

        {/* Section 4: Trading Psychology */}
        <LearnSection id="psychology" number={4} title="Trading Psychology" icon="🧠">
          {renderCards(psychologyCards)}
          <QuizCard {...quizPsychology} />
        </LearnSection>

        {/* CTA → Strategies */}
        <section className="learn-cta">
          <div className="learn-cta__inner">
            <div className="learn-cta__icon">🎯</div>
            <h3 className="learn-cta__heading">Ready to Apply This Knowledge?</h3>
            <p className="learn-cta__sub">
              You've built the foundation. Now learn proven trading strategies with
              detailed entry/exit rules, risk parameters, and real chart examples.
            </p>
            <button
              className="learn-cta__btn"
              onClick={() => navigate('/strategies')}
            >
              Explore Trading Strategies →
            </button>
            <div className="learn-cta__hint">
              <span className="learn-cta__hint-icon">💡</span>
              Each strategy includes a "Try in Simulator" button for hands-on practice
            </div>
          </div>
        </section>
      </main>
    </div>
  );
};

export default LearnPage;
