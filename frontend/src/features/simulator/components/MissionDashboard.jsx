// src/features/simulator/components/MissionDashboard.jsx
import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { MISSIONS } from '../utils/missions';
import { computeSMA } from '../utils/simulatorData';
import MissionDebriefModal from './MissionDebriefModal';
import CandlestickChart from './CandlestickChart';
import './MissionDashboard.css';

const MissionDashboard = () => {
  const { missionId } = useParams();
  const navigate = useNavigate();
  const mission = MISSIONS.find(m => m.id === missionId);

  const [candles, setCandles]       = useState([]);
  const [currentIndex, setIndex]    = useState(0);
  const [balance, setBalance]       = useState(mission?.startingBalance || 500000);
  const [position, setPosition]     = useState({ qty: 0, avgPrice: 0 });
  const [trades, setTrades]         = useState([]);
  const [maxDrawdown, setMaxDD]     = useState(0);
  const [isFinished, setFinished]   = useState(false);
  const [isPaused, setPaused]       = useState(false);
  const [assessment, setAssessment] = useState(null);
  const [qty, setQty]               = useState(10);

  const peakRef    = useRef(mission?.startingBalance || 500000);
  const balRef     = useRef(balance);
  const posRef     = useRef(position);
  const ddRef      = useRef(0);
  const idxRef     = useRef(0);
  const tradesRef  = useRef([]);

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

  // Timer
  useEffect(() => {
    if (isFinished || !mission || isPaused) return;

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
  }, [isFinished, mission, isPaused]);

  const finishMission = useCallback((forced) => {
    setFinished(true);
    const idx = idxRef.current;
    const price = mission?.dataset[idx]?.close || 0;
    const eq = balRef.current + (posRef.current.qty * price);
    const pnlAmt = eq - (mission?.startingBalance || 500000);
    const pnlPct = ((pnlAmt / (mission?.startingBalance || 500000)) * 100).toFixed(2);
    const result = mission.assess({
      finalBalance: eq,
      tradeCount: tradesRef.current.length,
      maxDrawdown: ddRef.current,
      forcedFail: forced,
    });
    // Attach stats for the modal to display
    result.stats = {
      equity: eq,
      pnlAmt,
      pnlPct,
      tradeCount: tradesRef.current.length,
      maxDrawdown: ddRef.current,
    };
    setAssessment(result);

    // Save to localStorage
    try {
      const saved = JSON.parse(localStorage.getItem('tl_missions') || '{}');
      // Only save if this is a better result
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

    setBalance(b => b + price * qty);
    setPosition(p => ({
      qty: p.qty - qty,
      avgPrice: p.qty - qty === 0 ? 0 : p.avgPrice,
    }));
    setTrades(t => [...t, { type: 'SELL', qty, price, time: candles[candles.length - 1].date }]);
  }, [isFinished, trades, mission, candles, qty, position]);

  const smaData = useMemo(() => candles.length > 0 ? computeSMA(candles, 7) : [], [candles]);

  if (!mission) return null;

  const currentPrice = candles.length > 0 ? candles[candles.length - 1].close : 0;
  const equity = balance + (position.qty * currentPrice);
  const pnl = equity - mission.startingBalance;
  const pnlPct = ((pnl / mission.startingBalance) * 100).toFixed(2);
  const tradesLeft = mission.constraints.maxTrades - trades.length;
  const progress = Math.round((currentIndex / (mission.dataset.length - 1)) * 100);
  const ddPct = maxDrawdown.toFixed(1);
  const positionPnl = position.qty > 0 ? (currentPrice - position.avgPrice) * position.qty : 0;

  return (
    <div className="msn-dash">
      {/* ── Top Strip ── */}
      <div className="msn-dash__strip">
        <div className="msn-dash__strip-left">
          <span className="msn-dash__badge">MISSION</span>
          <span className="msn-dash__mission-name">{mission.title}</span>
          <span className="msn-dash__mission-sub">{mission.subtitle}</span>
        </div>
        <div className="msn-dash__strip-right">
          <button className="msn-dash__pause" onClick={() => setPaused(p => !p)} disabled={isFinished}>
            {isPaused ? '▶ Resume' : '⏸ Pause'}
          </button>
          <button className="msn-dash__abort" onClick={() => finishMission(false)} disabled={isFinished}>
            ⏹ End Early
          </button>
        </div>
      </div>

      {/* ── Stats Bar ── */}
      <div className="msn-dash__stats">
        <div className="msn-stat">
          <span className="msn-stat__label">Equity</span>
          <span className="msn-stat__val">₹{equity.toLocaleString('en-IN', { maximumFractionDigits: 0 })}</span>
        </div>
        <div className="msn-stat__div" />
        <div className="msn-stat">
          <span className="msn-stat__label">P&L</span>
          <span className={`msn-stat__val ${pnl >= 0 ? 'msn-stat__val--up' : 'msn-stat__val--dn'}`}>
            {pnl >= 0 ? '+' : ''}{pnlPct}%
          </span>
        </div>
        <div className="msn-stat__div" />
        <div className="msn-stat">
          <span className="msn-stat__label">Cash</span>
          <span className="msn-stat__val">₹{balance.toLocaleString('en-IN', { maximumFractionDigits: 0 })}</span>
        </div>
        <div className="msn-stat__div" />
        <div className="msn-stat">
          <span className="msn-stat__label">Position</span>
          <span className="msn-stat__val">{position.qty} × {mission.ticker}</span>
        </div>
        <div className="msn-stat__div" />
        <div className="msn-stat">
          <span className="msn-stat__label">Trades Left</span>
          <span className={`msn-stat__val ${tradesLeft <= 1 ? 'msn-stat__val--dn' : ''}`}>{tradesLeft}</span>
        </div>
        <div className="msn-stat__div" />
        <div className="msn-stat">
          <span className="msn-stat__label">Drawdown</span>
          <span className={`msn-stat__val ${maxDrawdown > 10 ? 'msn-stat__val--dn' : ''}`}>{ddPct}%</span>
        </div>
        <div className="msn-stat__div" />
        <div className="msn-stat">
          <span className="msn-stat__label">Progress</span>
          <div className="msn-stat__progress">
            <div className="msn-stat__progress-fill" style={{ width: `${progress}%` }} />
          </div>
        </div>
      </div>

      {/* ── Main ── */}
      <div className="msn-dash__body">
        {/* Chart */}
        <div className="msn-dash__chart">
          <CandlestickChart candles={candles} smaData={smaData} symbol={mission.ticker} isLoading={false} />
        </div>

        {/* Order Panel */}
        <div className="msn-dash__panel">
          {/* Objective */}
          <div className="msn-obj">
            <h4 className="msn-obj__title">📋 Objective</h4>
            <p className="msn-obj__text">{mission.objective}</p>
          </div>

          {/* Position info */}
          {position.qty > 0 && (
            <div className="msn-pos">
              <div className="msn-pos__row">
                <span>Qty</span><span>{position.qty}</span>
              </div>
              <div className="msn-pos__row">
                <span>Avg Price</span><span>₹{position.avgPrice.toFixed(2)}</span>
              </div>
              <div className="msn-pos__row">
                <span>Unrealized P&L</span>
                <span className={positionPnl >= 0 ? 'msn-up' : 'msn-dn'}>
                  {positionPnl >= 0 ? '+' : ''}₹{positionPnl.toFixed(0)}
                </span>
              </div>
            </div>
          )}

          {/* Quantity */}
          <div className="msn-qty">
            <label className="msn-qty__label">Quantity</label>
            <div className="msn-qty__btns">
              {[1, 5, 10, 25, 50].map(q => (
                <button key={q} className={`msn-qty__btn${qty === q ? ' msn-qty__btn--on' : ''}`} onClick={() => setQty(q)}>
                  {q}
                </button>
              ))}
            </div>
          </div>

          {/* Buy / Sell */}
          <div className="msn-actions">
            <button
              className="msn-act msn-act--buy"
              onClick={handleBuy}
              disabled={isFinished || tradesLeft <= 0 || balance < currentPrice * qty}
            >
              BUY {qty} @ ₹{currentPrice.toFixed(0)}
            </button>
            <button
              className="msn-act msn-act--sell"
              onClick={handleSell}
              disabled={isFinished || tradesLeft <= 0 || position.qty < qty}
            >
              SELL {qty} @ ₹{currentPrice.toFixed(0)}
            </button>
          </div>

          {/* Trade Log */}
          {trades.length > 0 && (
            <div className="msn-log">
              <h4 className="msn-log__title">Trade Log</h4>
              {trades.map((t, i) => (
                <div key={i} className={`msn-log__row msn-log__row--${t.type.toLowerCase()}`}>
                  <span>{t.type}</span>
                  <span>{t.qty} × ₹{t.price.toFixed(0)}</span>
                  <span className="msn-log__date">{t.time}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

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
  );
};

export default MissionDashboard;
