import React from 'react';
import { useNavigate } from 'react-router-dom';
import './StrategyDetail.css';

const StrategyDetail = ({ strategy, onClose }) => {
  const navigate = useNavigate();

  if (!strategy) return null;

  const riskClass =
    strategy.risk === 'LOW' ? 'low' :
    strategy.risk === 'MEDIUM' ? 'medium' : 'high';

  return (
    <div className="strat-detail-overlay" onClick={onClose}>
      <div className="strat-detail" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="strat-detail__header">
          <div className="strat-detail__header-left">
            <span className="strat-detail__icon">{strategy.icon}</span>
            <div>
              <h2 className="strat-detail__name">{strategy.name}</h2>
              <div className="strat-detail__badges">
                <span className={`strat-detail__risk strat-detail__risk--${riskClass}`}>
                  {strategy.risk} RISK
                </span>
                <span className="strat-detail__category">{strategy.category}</span>
                <span className="strat-detail__time">{strategy.timeHorizon}</span>
              </div>
            </div>
          </div>
          <button className="strat-detail__close" onClick={onClose} aria-label="Close">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M5 5L15 15M15 5L5 15" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
            </svg>
          </button>
        </div>

        {/* Body */}
        <div className="strat-detail__body">
          {/* What is this strategy? */}
          <section className="strat-detail__section">
            <h3 className="strat-detail__section-title">
              <span className="strat-detail__section-num">01</span>
              What is this Strategy?
            </h3>
            <p className="strat-detail__text">{strategy.whatIs}</p>
          </section>

          {/* Entry & Exit in 2-column */}
          <div className="strat-detail__two-col">
            <section className="strat-detail__section strat-detail__section--entry">
              <h3 className="strat-detail__section-title">
                <span className="strat-detail__section-num">02</span>
                Entry Conditions
              </h3>
              <ul className="strat-detail__list strat-detail__list--entry">
                {strategy.entry.map((item, i) => (
                  <li key={i}>{item}</li>
                ))}
              </ul>
            </section>

            <section className="strat-detail__section strat-detail__section--exit">
              <h3 className="strat-detail__section-title">
                <span className="strat-detail__section-num">03</span>
                Exit Conditions
              </h3>
              <ul className="strat-detail__list strat-detail__list--exit">
                {strategy.exit.map((item, i) => (
                  <li key={i}>{item}</li>
                ))}
              </ul>
            </section>
          </div>

          {/* Risk Rules */}
          <section className="strat-detail__section">
            <h3 className="strat-detail__section-title">
              <span className="strat-detail__section-num">04</span>
              Risk Rules
            </h3>
            <ul className="strat-detail__list strat-detail__list--risk">
              {strategy.riskRules.map((item, i) => (
                <li key={i}>{item}</li>
              ))}
            </ul>
          </section>

          {/* Market Conditions */}
          <section className="strat-detail__section">
            <h3 className="strat-detail__section-title">
              <span className="strat-detail__section-num">05</span>
              Market Conditions
            </h3>
            <p className="strat-detail__text">{strategy.marketConditions}</p>
          </section>

          {/* How to Identify */}
          <section className="strat-detail__section">
            <h3 className="strat-detail__section-title">
              <span className="strat-detail__section-num">06</span>
              How to Identify in Real Market
            </h3>
            <ul className="strat-detail__list">
              {strategy.identification.map((item, i) => (
                <li key={i}>{item}</li>
              ))}
            </ul>
          </section>

          {/* Visual Chart Example */}
          <section className="strat-detail__section">
            <h3 className="strat-detail__section-title">
              <span className="strat-detail__section-num">07</span>
              Visual Chart Example
            </h3>
            <div className="strat-detail__chart-example">
              <StrategyChartVisual strategy={strategy} />
            </div>
          </section>
        </div>

        {/* Footer CTA */}
        <div className="strat-detail__footer">
          <button
            className="strat-detail__sim-btn"
            onClick={() => navigate(`/simulator?strategy=${strategy.slug}`)}
          >
            Try This Strategy in Simulator →
          </button>
        </div>
      </div>
    </div>
  );
};

/* ─── Mini chart visual for each strategy ─── */
const StrategyChartVisual = ({ strategy }) => {
  const chartData = strategy.chartData || getDefaultChartData(strategy.slug);
  const { candles, annotations } = chartData;

  const allPrices = candles.flatMap(c => [c.h, c.l]);
  const minP = Math.min(...allPrices);
  const maxP = Math.max(...allPrices);
  const range = maxP - minP || 1;
  const w = 480;
  const h = 180;
  const pad = 16;

  const priceToY = (p) => pad + ((maxP - p) / range) * (h - 2 * pad);
  const candleW = Math.min(16, (w - 2 * pad) / candles.length - 2);

  return (
    <svg viewBox={`0 0 ${w} ${h}`} className="strat-chart-svg">
      {/* Grid lines */}
      {[0.25, 0.5, 0.75].map((frac) => (
        <line
          key={frac}
          x1={pad} y1={pad + frac * (h - 2 * pad)}
          x2={w - pad} y2={pad + frac * (h - 2 * pad)}
          stroke="#30363d" strokeWidth="0.5" strokeDasharray="4,4"
        />
      ))}

      {/* Candles */}
      {candles.map((c, i) => {
        const x = pad + (i + 0.5) * ((w - 2 * pad) / candles.length);
        const isBull = c.c >= c.o;
        const color = isBull ? '#00ff88' : '#ff4d4f';
        const bodyTop = priceToY(Math.max(c.o, c.c));
        const bodyBot = priceToY(Math.min(c.o, c.c));

        return (
          <g key={i}>
            <line x1={x} y1={priceToY(c.h)} x2={x} y2={priceToY(c.l)}
              stroke={color} strokeWidth="1" />
            <rect x={x - candleW / 2} y={bodyTop}
              width={candleW} height={Math.max(bodyBot - bodyTop, 1)}
              fill={isBull ? 'rgba(0,255,136,0.25)' : 'rgba(255,77,79,0.7)'}
              stroke={color} strokeWidth="0.8" rx="1" />
          </g>
        );
      })}

      {/* Annotations */}
      {annotations && annotations.map((a, i) => {
        const x = pad + (a.index + 0.5) * ((w - 2 * pad) / candles.length);
        const y = priceToY(a.price);

        return (
          <g key={`ann-${i}`}>
            <circle cx={x} cy={y} r="4" fill="none" stroke="#00ff88" strokeWidth="1.5" />
            <text x={x + 8} y={y + 4} className="strat-chart-label">{a.label}</text>
          </g>
        );
      })}

      {/* Horizontal annotation lines */}
      {annotations && annotations.filter(a => a.line).map((a, i) => (
        <line key={`line-${i}`}
          x1={pad} y1={priceToY(a.price)}
          x2={w - pad} y2={priceToY(a.price)}
          stroke={a.lineColor || '#00ff88'} strokeWidth="0.8" strokeDasharray="6,4"
          opacity="0.5"
        />
      ))}
    </svg>
  );
};

/* Default chart data factory */
function getDefaultChartData(slug) {
  const presets = {
    scalping: {
      candles: [
        {o:100,h:102,l:99,c:101},{o:101,h:103,l:100,c:102},{o:102,h:104,l:101,c:101},
        {o:101,h:103,l:100,c:103},{o:103,h:105,l:102,c:104},{o:104,h:105,l:103,c:103},
        {o:103,h:104,l:101,c:102},{o:102,h:103,l:101,c:103},{o:103,h:106,l:102,c:105},
        {o:105,h:107,l:104,c:106},{o:106,h:107,l:105,c:105},{o:105,h:106,l:103,c:104},
      ],
      annotations: [
        {index:3,price:103,label:'Entry'},{index:5,price:103,label:'Exit +1%'},
        {index:8,price:105,label:'Entry'},{index:10,price:105,label:'Exit'},
      ]
    },
    'momentum-trading': {
      candles: [
        {o:80,h:82,l:79,c:81},{o:81,h:84,l:80,c:83},{o:83,h:87,l:82,c:86},
        {o:86,h:91,l:85,c:90},{o:90,h:95,l:89,c:94},{o:94,h:98,l:93,c:97},
        {o:97,h:100,l:95,c:96},{o:96,h:97,l:92,c:93},{o:93,h:94,l:90,c:91},
        {o:91,h:93,l:89,c:90},
      ],
      annotations: [
        {index:1,price:83,label:'Momentum Entry'},{index:5,price:97,label:'Take Profit'},
      ]
    },
    'support-resistance': {
      candles: [
        {o:100,h:105,l:99,c:104},{o:104,h:106,l:102,c:103},{o:103,h:104,l:99,c:100},
        {o:100,h:102,l:98,c:101},{o:101,h:105,l:100,c:104},{o:104,h:108,l:103,c:107},
        {o:107,h:110,l:106,c:109},{o:109,h:110,l:105,c:106},{o:106,h:107,l:103,c:104},
        {o:104,h:106,l:102,c:105},
      ],
      annotations: [
        {index:2,price:99,label:'Support',line:true,lineColor:'#00ff88'},
        {index:6,price:110,label:'Resistance',line:true,lineColor:'#ff4d4f'},
        {index:3,price:100,label:'Buy at Support'},
      ]
    },
    'sma-cross': {
      candles: [
        {o:95,h:97,l:94,c:96},{o:96,h:98,l:95,c:97},{o:97,h:99,l:96,c:98},
        {o:98,h:101,l:97,c:100},{o:100,h:103,l:99,c:102},{o:102,h:105,l:101,c:104},
        {o:104,h:106,l:103,c:105},{o:105,h:107,l:103,c:104},{o:104,h:105,l:102,c:103},
        {o:103,h:104,l:101,c:102},
      ],
      annotations: [
        {index:3,price:100,label:'SMA Cross ↑'},{index:7,price:104,label:'SMA Cross ↓'},
      ]
    },
    'buy-hold': {
      candles: [
        {o:100,h:103,l:98,c:102},{o:102,h:105,l:100,c:104},{o:104,h:106,l:102,c:105},
        {o:105,h:108,l:103,c:107},{o:107,h:109,l:105,c:106},{o:106,h:110,l:105,c:109},
        {o:109,h:113,l:108,c:112},{o:112,h:115,l:110,c:114},{o:114,h:117,l:112,c:116},
        {o:116,h:120,l:115,c:119},
      ],
      annotations: [
        {index:0,price:100,label:'Buy'},
        {index:9,price:119,label:'+19%'},
      ]
    },
    'dividend-investing': {
      candles: [
        {o:100,h:102,l:99,c:101},{o:101,h:103,l:100,c:102},{o:102,h:103,l:100,c:101},
        {o:101,h:104,l:100,c:103},{o:103,h:105,l:102,c:104},{o:104,h:106,l:103,c:105},
        {o:105,h:106,l:103,c:104},{o:104,h:106,l:103,c:105},{o:105,h:107,l:104,c:106},
        {o:106,h:108,l:105,c:107},
      ],
      annotations: [
        {index:2,price:101,label:'Div ₹2'},{index:5,price:105,label:'Div ₹2.5'},
        {index:8,price:106,label:'Div ₹3'},
      ]
    },
    'rsi-reversion': {
      candles: [
        {o:110,h:112,l:108,c:109},{o:109,h:110,l:105,c:106},{o:106,h:107,l:102,c:103},
        {o:103,h:104,l:100,c:101},{o:101,h:105,l:100,c:104},{o:104,h:108,l:103,c:107},
        {o:107,h:110,l:106,c:109},{o:109,h:112,l:108,c:111},{o:111,h:113,l:109,c:110},
        {o:110,h:112,l:108,c:111},
      ],
      annotations: [
        {index:3,price:101,label:'RSI < 30 Buy'},{index:7,price:111,label:'RSI > 70 Sell'},
      ]
    },
    'macd-strategy': {
      candles: [
        {o:100,h:102,l:99,c:101},{o:101,h:103,l:100,c:99},{o:99,h:100,l:97,c:98},
        {o:98,h:100,l:96,c:99},{o:99,h:102,l:98,c:101},{o:101,h:104,l:100,c:103},
        {o:103,h:106,l:102,c:105},{o:105,h:108,l:104,c:107},{o:107,h:108,l:105,c:106},
        {o:106,h:107,l:104,c:105},
      ],
      annotations: [
        {index:4,price:101,label:'MACD Cross ↑'},{index:8,price:106,label:'MACD Cross ↓'},
      ]
    },
  };

  return presets[slug] || presets['sma-cross'];
}

export default StrategyDetail;
