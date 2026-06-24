import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { MISSIONS } from '../utils/missions';
import MissionDebriefModal from './MissionDebriefModal';
import CandlestickChart from './CandlestickChart';
import OrderTicket from './OrderTicket';
import './SimulatorDashboard.css';

const MissionDashboard = () => {
  const { missionId } = useParams();
  const navigate = useNavigate();
  const mission = MISSIONS.find(m => m.id === missionId);
  
  const [candles, setCandles] = useState([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [balance, setBalance] = useState(mission?.startingBalance || 100000);
  const [position, setPosition] = useState({ qty: 0, averagePrice: 0 });
  const [trades, setTrades] = useState([]);
  const [maxDrawdown, setMaxDrawdown] = useState(0);
  const [isFinished, setIsFinished] = useState(false);
  const [assessment, setAssessment] = useState(null);

  const highestBalanceRef = useRef(mission?.startingBalance || 100000);
  // Keep refs in sync so the interval can read live values without being a dep
  const balanceRef = useRef(balance);
  const positionRef = useRef(position);
  const maxDrawdownRef = useRef(0);
  const currentIndexRef = useRef(0);
  useEffect(() => { balanceRef.current = balance; }, [balance]);
  useEffect(() => { positionRef.current = position; }, [position]);
  useEffect(() => { maxDrawdownRef.current = maxDrawdown; }, [maxDrawdown]);
  useEffect(() => { currentIndexRef.current = currentIndex; }, [currentIndex]);

  useEffect(() => {
    if (!mission) navigate('/missions');
    
    // Seed initial candles (start with 5 context candles)
    if (mission && currentIndex === 0) {
      setCandles(mission.dataset.slice(0, 5));
      setCurrentIndex(4);
    }
  }, [mission, navigate, currentIndex]);

  useEffect(() => {
    if (isFinished || !mission) return;

    const timer = setInterval(() => {
      setCurrentIndex(prev => {
        const next = prev + 1;
        if (next >= mission.dataset.length) {
          clearInterval(timer);
          // Read live balance/position via refs, not stale closure values
          const finalPrice = mission.dataset[prev]?.close || 0;
          const finalEquity = balanceRef.current + (positionRef.current.qty * finalPrice);
          const history = {
            finalBalance: finalEquity,
            tradeCount: 0, // will be overridden by endMission
            maxDrawdown: maxDrawdownRef.current,
            forcedFail: false
          };
          setIsFinished(true);
          setAssessment(mission.assess({ ...history, get tradeCount() { return 0; } }));
          return prev;
        }

        const nextCandle = mission.dataset[next];
        setCandles(old => [...old, nextCandle]);

        // Update live drawdown using refs (no stale closure)
        const currentPrice = nextCandle.close;
        const equity = balanceRef.current + (positionRef.current.qty * currentPrice);

        if (equity > highestBalanceRef.current) {
          highestBalanceRef.current = equity;
        }

        const currentDrawdown = ((highestBalanceRef.current - equity) / highestBalanceRef.current) * 100;
        if (currentDrawdown > maxDrawdownRef.current) {
          setMaxDrawdown(currentDrawdown);
        }

        // Check fail conditions live
        if (mission.constraints.maxDrawdownPercent && currentDrawdown > mission.constraints.maxDrawdownPercent) {
          clearInterval(timer);
          setIsFinished(true);
          setAssessment(mission.assess({
            finalBalance: equity,
            tradeCount: 0,
            maxDrawdown: currentDrawdown,
            forcedFail: true
          }));
        }

        return next;
      });
    }, 2000); // 2 second ticks

    return () => clearInterval(timer);
  // Only restart if the mission itself changes or it finishes — NOT on balance/position changes
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isFinished, mission]);

  const endMission = useCallback((forcedFail = false) => {
    setIsFinished(true);
    // Read live values from refs to avoid stale closure
    const idx = currentIndexRef.current;
    const finalCurrentPrice = mission?.dataset[idx]?.close || 0;
    const finalEquity = balanceRef.current + (positionRef.current.qty * finalCurrentPrice);

    setTrades(currentTrades => {
      const history = {
        finalBalance: finalEquity,
        tradeCount: currentTrades.length,
        maxDrawdown: maxDrawdownRef.current,
        forcedFail: forcedFail
      };
      const result = mission.assess(history);
      setAssessment(result);
      return currentTrades;
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mission]);

  const handleTrade = (type, qty, orderType) => {
    if (isFinished) return;
    
    if (trades.length >= mission.constraints.maxTrades) {
      alert("Mission Constraint Reached: Max Trades exceeded.");
      return;
    }

    const price = candles[candles.length - 1].close;
    const total = price * qty;

    if (type === 'BUY') {
      if (balance < total) {
        alert("Insufficient funds.");
        return;
      }
      setBalance(b => b - total);
      setPosition(p => ({
        qty: p.qty + qty,
        averagePrice: ((p.qty * p.averagePrice) + total) / (p.qty + qty)
      }));
    } else if (type === 'SELL') {
      if (position.qty < qty) {
        alert("Insufficient shares.");
        return;
      }
      setBalance(b => b + total);
      setPosition(p => ({
        qty: p.qty - qty,
        averagePrice: p.qty - qty === 0 ? 0 : p.averagePrice
      }));
    }

    setTrades(old => [...old, { type, qty, price, time: new Date().toISOString() }]);
  };

  if (!mission) return null;

  const currentPrice = candles.length > 0 ? candles[candles.length - 1].close : 0;

  return (
    <div className="simulator-dashboard">
      <div className="dashboard-header" style={{ justifyContent: 'space-between' }}>
        <h2 style={{ color: '#ff7b72' }}>MISSION ACTIVE: {mission.title}</h2>
        <div style={{ display: 'flex', gap: '20px' }}>
          <span style={{ color: trades.length >= mission.constraints.maxTrades ? 'red' : '#c9d1d9' }}>
            Trades: {trades.length} / {mission.constraints.maxTrades}
          </span>
          {mission.constraints.maxDrawdownPercent && (
            <span style={{ color: maxDrawdown >= mission.constraints.maxDrawdownPercent ? 'red' : '#c9d1d9' }}>
              Drawdown: {maxDrawdown.toFixed(1)}% / {mission.constraints.maxDrawdownPercent}%
            </span>
          )}
          <button 
            onClick={() => endMission()}
            style={{ padding: '5px 15px', backgroundColor: '#30363d', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>
            Abort / End Early
          </button>
        </div>
      </div>

      <div className="dashboard-content">
        <div className="dashboard-left">
          <CandlestickChart candles={candles} />
        </div>
        
        <div className="dashboard-right">
          <div style={{ backgroundColor: '#0d1117', padding: '15px', border: '1px solid #30363d', borderRadius: '8px', marginBottom: '15px' }}>
            <h3 style={{ margin: '0 0 10px 0', color: '#c9d1d9' }}>Mission Objective</h3>
            <p style={{ fontSize: '14px', color: '#8b949e', margin: 0 }}>{mission.objective}</p>
          </div>

          <OrderTicket 
            stock={{ symbol: 'SPY', price: currentPrice }}
            portfolio={{ cash: balance, holdings: { SPY: { qty: position.qty, avgPrice: position.averagePrice } } }}
            onPlaceOrder={handleTrade}
          />
        </div>
      </div>

      {assessment && (
        <MissionDebriefModal assessment={assessment} onClose={() => setAssessment(null)} />
      )}
    </div>
  );
};

export default MissionDashboard;
