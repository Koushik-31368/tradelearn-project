// src/components/LiveScoreboard.jsx
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { backendUrl } from '../utils/api';
import './LiveScoreboard.css';

/**
 * Esports-style live scoreboard that polls the position endpoint every 3 s.
 *
 * Props:
 *   gameId        – match id
 *   userId        – player to track
 *   currentPrice  – latest candle close (for live equity calc)
 *   startingBalance – to compute P&L %
 *   label         – display name ("You" / opponent name)
 *   accent        – "green" | "red" (side colour)
 *   gameOver      – stops polling when true
 */
const LiveScoreboard = ({
    gameId,
    userId,
    currentPrice = 0,
    startingBalance = 1000000,
    label = 'Player',
    accent = 'green',
    gameOver = false,
}) => {
    const [pos, setPos] = useState(null);
    const intervalRef = useRef(null);

    const fetchPosition = useCallback(async () => {
        if (!gameId || !userId) return;
        try {
            const res = await fetch(backendUrl(`/api/match/${gameId}/position/${userId}`));
            if (res.ok) setPos(await res.json());
        } catch {
            /* silent — will retry in 3 s */
        }
    }, [gameId, userId]);

    // Initial fetch + 3-second polling
    useEffect(() => {
        if (gameOver) return;
        fetchPosition();
        intervalRef.current = setInterval(fetchPosition, 3000);
        return () => clearInterval(intervalRef.current);
    }, [fetchPosition, gameOver]);

    // Stop polling on game over
    useEffect(() => {
        if (gameOver && intervalRef.current) clearInterval(intervalRef.current);
    }, [gameOver]);

    // ── Derived stats ──
    const equity = computeEquity(pos, currentPrice);
    const pnl = equity - startingBalance;
    const pnlPct = startingBalance > 0 ? (pnl / startingBalance) * 100 : 0;

    const peakEquity     = pos?.peakEquity ?? startingBalance;
    const maxDrawdown    = pos?.maxDrawdown ?? 0;           // 0.0 – 1.0
    const totalTrades    = pos?.totalTrades ?? 0;
    const profitTrades   = pos?.profitableTrades ?? 0;
    const accuracy       = totalTrades > 0 ? (profitTrades / totalTrades) * 100 : 0;

    const isGreen = accent === 'green';

    return (
        <div className={`scoreboard ${isGreen ? 'sb-green' : 'sb-red'}`}>
            <div className="sb-label">{label}</div>

            {/* ── Equity ── */}
            <div className="sb-row sb-equity">
                <span className="sb-key">EQUITY</span>
                <span className={`sb-val ${pnl >= 0 ? 'sb-up' : 'sb-down'}`}>
                    ₹{equity.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 0 })}
                </span>
            </div>

            {/* ── P&L badge ── */}
            <div className={`sb-pnl-badge ${pnl >= 0 ? 'sb-up' : 'sb-down'}`}>
                {pnl >= 0 ? '▲' : '▼'} {Math.abs(pnlPct).toFixed(2)}%
            </div>

            {/* ── Stat pills ── */}
            <div className="sb-stats">
                <div className="sb-pill">
                    <span className="sb-pill-label">PEAK</span>
                    <span className="sb-pill-value">₹{peakEquity.toLocaleString(undefined, { maximumFractionDigits: 0 })}</span>
                </div>
                <div className="sb-pill">
                    <span className="sb-pill-label">DRAWDOWN</span>
                    <span className={`sb-pill-value ${maxDrawdown > 0.1 ? 'sb-down' : ''}`}>
                        {(maxDrawdown * 100).toFixed(1)}%
                    </span>
                </div>
                <div className="sb-pill">
                    <span className="sb-pill-label">ACCURACY</span>
                    <span className="sb-pill-value">{accuracy.toFixed(0)}%</span>
                </div>
                <div className="sb-pill">
                    <span className="sb-pill-label">TRADES</span>
                    <span className="sb-pill-value">{totalTrades}</span>
                </div>
            </div>

            {/* ── Accuracy bar ── */}
            <div className="sb-bar-track">
                <div
                    className="sb-bar-fill"
                    style={{ width: `${Math.min(accuracy, 100)}%` }}
                />
            </div>
        </div>
    );
};

/** Cash + long value − short value at current mark price */
function computeEquity(pos, price) {
    if (!pos) return 0;
    let eq = pos.cash || 0;
    for (const qty of Object.values(pos.longShares || pos.shares || {})) eq += qty * price;
    for (const qty of Object.values(pos.shortShares || {})) eq -= qty * price;
    return eq;
}

export default LiveScoreboard;
