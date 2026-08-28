// src/features/simulator/components/MissionDashboard.jsx
import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { MISSIONS } from '../utils/missions';
import { computeSMA } from '../utils/simulatorData';
import MissionDebriefModal from './MissionDebriefModal';
import CandlestickChart from './CandlestickChart';
import './MissionDashboard.css';

/* ── Briefing Countdown Overlay ───────────────────────────────── */
const BriefingOverlay = ({ mission, onDone }) => {
  const [count, setCount]     = useState(3);
  const [fading, setFading]   = useState(false);
  const [removed, setRemoved] = useState(false);

  useEffect(() => {
    const t1 = setTimeout(() => setCount(2), 1000);
    const t2 = setTimeout(() => setCount(1), 2000);
    const t3 = setTimeout(() => setCount('GO!'), 3000);
    // Start fade + call onDone at the same time (chart renders behind overlay)
    const t4 = setTimeout(() => { setFading(true); onDone(); }, 3500);
    // Remove from DOM after fade completes
    const t5 = setTimeout(() => setRemoved(true), 3950);
    return () => [t1, t2, t3, t4, t5].forEach(clearTimeout);
  }, [onDone]);

  if (removed) return null;

  return (
    <div className={`msn-brief${fading ? ' msn-brief--fading' : ''}`}>
      <div className="msn-brief__grid" />
      <div className="msn-brief__scan" />
      <div className="msn-brief__tag">▸ Mission Briefing</div>
      <div className="msn-brief__mission">{mission.title}</div>
      <div className="msn-brief__sub">{mission.subtitle}</div>
      <div
        key={count}
        className={`msn-brief__count${count === 'GO!' ? ' msn-brief__count--go' : ''}`}
      >
        {count}
      </div>
      <p className="msn-brief__obj">🎯 {mission.objective}</p>
    </div>
  );
};

/* ── Screen Flash ───────────────────────────────────────── */
const ScreenFlash = ({ type }) => {
  const [active, setActive] = useState(false);
  const prevType = useRef(null);

  useEffect(() => {
    if (type && type !== prevType.current) {
      prevType.current = type;
      setActive(true);
      const t = setTimeout(() => { setActive(false); prevType.current = null; }, 400);
      return () => clearTimeout(t);
    }
  }, [type]);

  if (!type) return null;
  return (
    <div className={`msn-flash msn-flash--${type}${active ? ' msn-flash--active' : ''}`} />
  );
};

/* ── Main Component ─────────────────────────────────────── */
const MissionDashboard = () => {
  const { missionId } = useParams();
  const navigate = useNavigate();
  const mission = MISSIONS.find(m => m.id === missionId);

  const [briefingDone, setBriefingDone]   = useState(false);
  const [candles, setCandles]             = useState([]);
  const [currentIndex, setIndex]          = useState(0);
  const [balance, setBalance]             = useState(mission?.startingBalance || 500000);
  const [position, setPosition]           = useState({ qty: 0, avgPrice: 0 });
  const [trades, setTrades]               = useState([]);
  const [maxDrawdown, setMaxDD]           = useState(0);
  const [isFinished, setFinished]         = useState(false);
  const [isPaused, setPaused]             = useState(false);
  const [assessment, setAssessment]       = useState(null);
  const [qty, setQty]                     = useState(10);
  const [flashType, setFlashType]         = useState(null);
  const [equityFlash, setEquityFlash]     = useState(false);

  const peakRef   = useRef(mission?.startingBalance || 500000);
  const balRef    = useRef(balance);
  const posRef    = useRef(position);
  const ddRef     = useRef(0);
  const idxRef    = useRef(0);
  const tradesRef = useRef([]);

  useEffect(() => { balRef.current = balance; }, [balance]);
  useEffect(() => { posRef.current = position; }, [position]);
  useEffect(() => { ddRef.current = maxDrawdown; }, [maxDrawdown]);
  useEffect(() => { idxRef.current = currentIndex; }, [currentIndex]);
  useEffect(() => { tradesRef.current = trades; }, [trades]);

  // Init
  useEffect(() => {
    if (!mission) { navigate('/missions'); return; }
    setCandles(mission.dataset.slice(0, 5));
    setIndex(4);
  }, [mission, navigate]);

  // Timer — only ticks after briefing is done
  useEffect(() => {
    if (!briefingDone || isFinished || !mission || isPaused) return;

    const timer = setInterval(() => {
      setIndex(prev => {
        const next = prev + 1;
        if (next >= mission.dataset.length) {
          clearInterval(timer);
          finishMission(false);
          return prev;
        }

        const candle = mission.dataset[next];
        setCandles(old => [...old, candle]);

        // Equity flash on new candle
        setEquityFlash(true);
        setTimeout(() => setEquityFlash(false), 400);

        // Drawdown check
        const eq = balRef.current + (posRef.current.qty * candle.close);
        if (eq > peakRef.current) peakRef.current = eq;
        const dd = ((peakRef.current - eq) / peakRef.current) * 100;
        if (dd > ddRef.current) setMaxDD(dd);

        // Constraint check
        if (mission.constraints.maxDrawdownPercent && dd > mission.constraints.maxDrawdownPercent) {
          clearInterval(timer);
          finishMission(true);
        }

        return next;
      });
    }, mission.constraints.timePerCandle || 2000);

    return () => clearInterval(timer);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [briefingDone, isFinished, mission, isPaused]);

  const finishMission = useCallback((forced) => {
    setFinished(true);
    const idx   = idxRef.current;
    const price = mission?.dataset[idx]?.close || 0;
    const eq    = balRef.current + (posRef.current.qty * price);
    const pnlAmt = eq - (mission?.startingBalance || 500000);
    const pnlPct = ((pnlAmt / (mission?.startingBalance || 500000)) * 100).toFixed(2);

    const result = mission.assess({
      finalBalance: eq,
      tradeCount:   tradesRef.current.length,
      maxDrawdown:  ddRef.current,
      forcedFail:   forced,
    });
    result.stats = { equity: eq, pnlAmt, pnlPct, tradeCount: tradesRef.current.length, maxDrawdown: ddRef.current };
    setAssessment(result);

    try {
      const saved = JSON.parse(localStorage.getItem('tl_missions') || '{}');
      if (!saved[mission.id] || result.status === 'PASS') {
        saved[mission.id] = { status: result.status, grade: result.grade, title: result.title };
        localStorage.setItem('tl_missions', JSON.stringify(saved));
      }
    } catch { /* empty */ }
  }, [mission]);

  const handleBuy = useCallback(() => {
    if (isFinished || trades.length >= mission.constraints.maxTrades) return;
    const price = candles[candles.length - 1]?.close;
    if (!price) return;
    const cost = price * qty;
    if (cost > balance) return;

    setFlashType('buy');
    setTimeout(() => setFlashType(null), 500);

    setBalance(b => b - cost);
    setPosition(p => ({
      qty: p.qty + qty,
      avgPrice: p.qty + qty > 0 ? ((p.qty * p.avgPrice) + cost) / (p.qty + qty) : price,
    }));
    setTrades(t => [...t, { type: 'BUY', qty, price, time: candles[candles.length - 1].date }]);
  }, [isFinished, trades, mission, candles, qty, balance]);

  const handleSell = useCallback(() => {
    if (isFinished || trades.length >= mission.constraints.maxTrades) return;
    const price = candles[candles.length - 1]?.close;
    if (!price || position.qty < qty) return;

    setFlashType('sell');
    setTimeout(() => setFlashType(null), 500);

    setBalance(b => b + price * qty);
    setPosition(p => ({
      qty: p.qty - qty,
      avgPrice: p.qty - qty === 0 ? 0 : p.avgPrice,
    }));
    setTrades(t => [...t, { type: 'SELL', qty, price, time: candles[candles.length - 1].date }]);
  }, [isFinished, trades, mission, candles, qty, position]);

  const smaData = useMemo(() => candles.length > 0 ? computeSMA(candles, 7) : [], [candles]);

  if (!mission) return null;

  const currentPrice  = candles.length > 0 ? candles[candles.length - 1].close : 0;
  const equity        = balance + (position.qty * currentPrice);
  const pnl           = equity - mission.startingBalance;
  const pnlPct        = ((pnl / mission.startingBalance) * 100).toFixed(2);
  const tradesLeft    = mission.constraints.maxTrades - trades.length;
  const progress      = Math.round((currentIndex / Math.max(mission.dataset.length - 1, 1)) * 100);
  const positionPnl   = position.qty > 0 ? (currentPrice - position.avgPrice) * position.qty : 0;

  const ddClass = maxDrawdown >= 10
    ? 'msn-dd-pill--danger'
    : maxDrawdown >= 5
    ? 'msn-dd-pill--warn'
    : '';

  // Build trade slots
  const totalSlots = mission.constraints.maxTrades;
  const usedSlots  = trades.length;

  const formatInr = n => Math.abs(n).toLocaleString('en-IN', { maximumFractionDigits: 0 });

  return (
    <>
      {/* Briefing overlay */}
      {!briefingDone && (
        <BriefingOverlay mission={mission} onDone={() => setBriefingDone(true)} />
      )}

      {/* Screen flash */}
      <ScreenFlash type={flashType} />

      <div className="msn-dash">
        {/* ── HUD Strip ── */}
        <div className="msn-hud">
          <div className="msn-hud__left">
            <div className="msn-live">
              <div className="msn-live__dot" />
              <span className="msn-live__text">LIVE</span>
            </div>
            <span className="msn-hud__name">{mission.title}</span>
            <span className="msn-hud__sub">{mission.subtitle}</span>
          </div>

          <div className="msn-hud__center">
            {/* Trade slots */}
            <div className="msn-slots">
              <span className="msn-slots__label">Trades</span>
              {Array.from({ length: totalSlots }).map((_, i) => (
                <div
                  key={i}
                  className={`msn-slot ${i < usedSlots ? 'msn-slot--used' : 'msn-slot--avail'}`}
                />
              ))}
            </div>
          </div>

          <div className="msn-hud__right">
            <button
              id="msn-pause-btn"
              className="msn-hud__btn"
              onClick={() => setPaused(p => !p)}
              disabled={isFinished}
            >
              {isPaused ? '▶ RESUME' : '⏸ PAUSE'}
            </button>
            <button
              id="msn-end-btn"
              className="msn-hud__btn msn-hud__btn--abort"
              onClick={() => finishMission(false)}
              disabled={isFinished}
            >
              ⏹ END
            </button>
          </div>
        </div>

        {/* ── Stats Bar ── */}
        <div className="msn-statsbar">
          <div className="msn-kpi">
            <span className="msn-kpi__label">Equity</span>
            <span className={`msn-kpi__val${equityFlash ? ' msn-kpi__val--flash' : ''}`}>
              ₹{formatInr(equity)}
            </span>
          </div>

          <div className="msn-kpi">
            <span className="msn-kpi__label">P&amp;L</span>
            <span className={`msn-kpi__val ${pnl >= 0 ? 'msn-kpi__val--up' : 'msn-kpi__val--dn'}`}>
              {pnl >= 0 ? '+' : ''}{pnlPct}%
            </span>
          </div>

          <div className="msn-kpi">
            <span className="msn-kpi__label">Cash</span>
            <span className="msn-kpi__val">₹{formatInr(balance)}</span>
          </div>

          <div className="msn-kpi">
            <span className="msn-kpi__label">Position</span>
            <span className="msn-kpi__val">
              {position.qty > 0 ? `${position.qty} × ${mission.ticker}` : '—'}
            </span>
          </div>

          <div className="msn-kpi">
            <span className="msn-kpi__label">Drawdown</span>
            <div className={`msn-dd-pill ${ddClass}`}>
              {maxDrawdown.toFixed(1)}%
              {mission.constraints.maxDrawdownPercent && (
                <span style={{ opacity: 0.5, fontSize: 9 }}>/ {mission.constraints.maxDrawdownPercent}%</span>
              )}
            </div>
          </div>

          <div className="msn-kpi msn-kpi--progress">
            <span className="msn-kpi__label">Timeline</span>
            <div className="msn-timeline">
              <div className="msn-timeline__track">
                <div className="msn-timeline__fill" style={{ width: `${progress}%` }} />
              </div>
              <span className="msn-timeline__pct">{progress}%</span>
            </div>
          </div>
        </div>

        {/* ── Main Body ── */}
        <div className="msn-body">
          {/* Chart — always rendered, briefing overlay sits on top (fixed) */}
          <div className="msn-chartzone">
            <CandlestickChart
              candles={candles}
              smaData={smaData}
              symbol={mission.ticker}
              isLoading={false}
            />
          </div>

          {/* Right Panel */}
          <div className="msn-panel">
            {/* Objective */}
            <div className="msn-section msn-obj">
              <div className="msn-obj__header">
                <span className="msn-obj__icon">🎯</span>
                <span className="msn-obj__title">Objective</span>
              </div>
              <p className="msn-obj__text">{mission.objective}</p>
            </div>

            {/* Position */}
            <div className="msn-section msn-pos">
              <div className="msn-pos__header">Open Position</div>
              {position.qty > 0 ? (
                <>
                  <div className={`msn-pos__pnl ${positionPnl >= 0 ? 'msn-pos__pnl--up' : 'msn-pos__pnl--dn'}`}>
                    {positionPnl >= 0 ? '+' : ''}₹{formatInr(positionPnl)}
                  </div>
                  <div className="msn-pos__rows">
                    <div className="msn-pos__row">
                      <span>Qty</span><span>{position.qty} × {mission.ticker}</span>
                    </div>
                    <div className="msn-pos__row">
                      <span>Avg Price</span><span>₹{position.avgPrice.toFixed(0)}</span>
                    </div>
                    <div className="msn-pos__row">
                      <span>Current</span><span>₹{currentPrice.toFixed(0)}</span>
                    </div>
                  </div>
                </>
              ) : (
                <div className="msn-pos__empty">No open position</div>
              )}
            </div>

            {/* Quantity */}
            <div className="msn-section msn-qty">
              <div className="msn-qty__header">Quantity</div>
              <div className="msn-qty__grid">
                {[1, 5, 10, 25, 50].map(q => (
                  <button
                    key={q}
                    id={`msn-qty-${q}`}
                    className={`msn-qty__pill${qty === q ? ' msn-qty__pill--on' : ''}`}
                    onClick={() => setQty(q)}
                  >
                    {q}
                  </button>
                ))}
              </div>
            </div>

            {/* BUY / SELL */}
            <div className="msn-actions">
              <button
                id="msn-buy-btn"
                className="msn-act msn-act--buy"
                onClick={handleBuy}
                disabled={isFinished || tradesLeft <= 0 || balance < currentPrice * qty}
              >
                ▲ BUY
                <span className="msn-act__sub">{qty} × ₹{currentPrice.toFixed(0)}</span>
              </button>
              <button
                id="msn-sell-btn"
                className="msn-act msn-act--sell"
                onClick={handleSell}
                disabled={isFinished || tradesLeft <= 0 || position.qty < qty}
              >
                ▼ SELL
                <span className="msn-act__sub">{qty} × ₹{currentPrice.toFixed(0)}</span>
              </button>
            </div>

            {/* Trade Log */}
            <div className="msn-section msn-log">
              <div className="msn-log__header">Trade Log ({trades.length}/{mission.constraints.maxTrades})</div>
              {trades.length === 0 ? (
                <div className="msn-log__empty">No trades yet</div>
              ) : (
                <div className="msn-log__rows">
                  {[...trades].reverse().map((t, i) => (
                    <div key={i} className="msn-log__row">
                      <span className={`msn-log__type msn-log__type--${t.type.toLowerCase()}`}>{t.type}</span>
                      <span className="msn-log__detail">{t.qty} @ ₹{t.price.toFixed(0)}</span>
                      <span className="msn-log__date">{t.time}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Debrief modal */}
        {assessment && (
          <MissionDebriefModal
            assessment={assessment}
            onClose={() => {
              setAssessment(null);
              if (assessment.nextMission === 'completed') {
                navigate('/missions');
              } else if (assessment.status === 'PASS' && assessment.nextMission) {
                navigate(`/mission-dashboard/${assessment.nextMission}`);
              } else {
                navigate('/missions');
              }
            }}
          />
        )}
      </div>
    </>
  );
};

export default MissionDashboard;
