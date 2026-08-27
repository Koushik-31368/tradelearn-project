// src/features/simulator/components/CandlestickChart.jsx
import React, { useMemo, useState, useCallback } from 'react';
import './CandlestickChart.css';

const W       = 800;
const H       = 380;
const VOL_H   = 50;
const PAD     = { top: 20, right: 66, bottom: 4, left: 10 };
const USABLE_W = W - PAD.left - PAD.right;
const USABLE_H = H - PAD.top  - PAD.bottom - VOL_H - 8;

const TIMEFRAMES = [
  { label: '1W', count: 5  },
  { label: '1M', count: 22 },
  { label: '3M', count: 66 },
  { label: 'All', count: Infinity },
];

/* ── Skeleton ── */
function ChartSkeleton() {
  return (
    <div className="cc-skel">
      <div className="cc-skel__row"><div className="cc-skel__pill cc-skel__pill--w"/><div className="cc-skel__pill"/></div>
      <div className="cc-skel__bars">
        {Array.from({ length: 24 }).map((_, i) => (
          <div key={i} className="cc-skel__bar" style={{ height: `${35 + Math.sin(i * 0.6) * 28 + 18}px`, animationDelay: `${i * 50}ms` }} />
        ))}
      </div>
    </div>
  );
}

/* ── Main ── */
const CandlestickChart = ({
  candles = [], smaData = [], symbol, isLoading = false,
  /* Replay props */
  isPlaying, onReplay, speedIdx, onSpeedChange, speeds = [], visibleCount = 0, totalCandles = 0, onSeek,
}) => {
  const [showSMA, setShowSMA]   = useState(true);
  const [tfLabel, setTfLabel]   = useState('1M');
  const [hoverIdx, setHoverIdx] = useState(null);

  const tf = TIMEFRAMES.find((t) => t.label === tfLabel) || TIMEFRAMES[1];
  const visible = candles.slice(-Math.min(tf.count, candles.length));
  const visSMA  = smaData.slice(-Math.min(tf.count, smaData.length));

  const chart = useMemo(() => {
    if (visible.length === 0) return null;
    const pMin   = Math.min(...visible.map((c) => c.low))  * 0.997;
    const pMax   = Math.max(...visible.map((c) => c.high)) * 1.003;
    const vMax   = Math.max(...visible.map((c) => c.volume), 1);
    const pRange = pMax - pMin || 1;
    const n      = visible.length;
    const cw     = Math.min(USABLE_W / n, 20);
    const gap    = (USABLE_W - cw * n) / (n + 1);

    const items = visible.map((c, i) => {
      const x  = PAD.left + gap + i * (cw + gap);
      const up = c.close >= c.open;
      const bt = PAD.top + ((pMax - Math.max(c.open, c.close)) / pRange) * USABLE_H;
      const bb = PAD.top + ((pMax - Math.min(c.open, c.close)) / pRange) * USABLE_H;
      const wt = PAD.top + ((pMax - c.high) / pRange) * USABLE_H;
      const wb = PAD.top + ((pMax - c.low)  / pRange) * USABLE_H;
      const vh = (c.volume / vMax) * VOL_H;
      return { ...c, x, cw, up, bt, bb, wt, wb, vh, vy: H - vh };
    });

    let smaP = '';
    if (showSMA && visSMA.length > 0) {
      smaP = visSMA.map((v, i) => {
        if (v == null) return null;
        return `${PAD.left + gap + i * (cw + gap) + cw / 2},${PAD.top + ((pMax - v) / pRange) * USABLE_H}`;
      }).filter(Boolean).join(' ');
    }

    const grid = Array.from({ length: 6 }, (_, i) => ({
      price: pMin + (pRange * i) / 5,
      y: PAD.top + USABLE_H - (USABLE_H * i) / 5,
    }));

    return { items, smaP, grid };
  }, [visible, visSMA, showSMA]);

  const onMove = useCallback((e) => {
    if (!chart) return;
    const r = e.currentTarget.getBoundingClientRect();
    const mx = ((e.clientX - r.left) / r.width) * W;
    let best = null, bd = Infinity;
    chart.items.forEach((c, i) => { const d = Math.abs(mx - (c.x + c.cw / 2)); if (d < bd) { bd = d; best = i; } });
    setHoverIdx(best);
  }, [chart]);

  if (isLoading) return <div className="cc-wrap"><ChartSkeleton /></div>;

  if (candles.length === 0) {
    return (
      <div className="cc-wrap">
        <div className="cc-empty"><span className="cc-empty__ico">📊</span>Select a stock to view chart</div>
      </div>
    );
  }

  const last = visible[visible.length - 1];
  const prev = visible.length > 1 ? visible[visible.length - 2] : last;
  const chg  = last.close - prev.close;
  const chgP = ((chg / prev.close) * 100).toFixed(2);
  const up   = chg >= 0;
  const hc   = hoverIdx !== null ? chart?.items[hoverIdx] : null;

  return (
    <div className="cc-wrap">
      {/* ── Header Row 1: Symbol + Price + Replay ── */}
      <div className="cc-head">
        <div className="cc-head__left">
          <span className="cc-sym">{symbol}</span>
          <span className="cc-price">₹{last.close.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
          <span className={`cc-chg ${up ? 'cc-chg--up' : 'cc-chg--dn'}`}>
            {up ? '+' : ''}{chg.toFixed(2)} ({up ? '+' : ''}{chgP}%)
          </span>
        </div>
        <div className="cc-head__right">
          {/* Replay controls */}
          {onReplay && (
            <div className="cc-replay">
              <button className={`cc-play${isPlaying ? ' cc-play--on' : ''}`} onClick={onReplay} disabled={isLoading}>
                {isPlaying ? '⏸' : visibleCount >= totalCandles ? '↺' : '▶'}
              </button>
              {speeds.map((s, i) => (
                <button key={s.label} className={`cc-spd${speedIdx === i ? ' cc-spd--on' : ''}`} onClick={() => onSpeedChange(i)}>
                  {s.label}
                </button>
              ))}
              <input
                type="range" className="cc-seek"
                min={10} max={totalCandles || 90} value={visibleCount}
                onChange={(e) => onSeek?.(Number(e.target.value))}
                disabled={isLoading || totalCandles === 0}
              />
              <span className="cc-cnt">{visibleCount}/{totalCandles}</span>
            </div>
          )}
        </div>
      </div>

      {/* ── Header Row 2: Timeframes + SMA ── */}
      <div className="cc-toolbar">
        <div className="cc-tfs">
          {TIMEFRAMES.map((t) => (
            <button key={t.label} className={`cc-tf${tfLabel === t.label ? ' cc-tf--on' : ''}`} onClick={() => setTfLabel(t.label)}>
              {t.label}
            </button>
          ))}
        </div>
        <button className={`cc-sma-btn${showSMA ? ' cc-sma-btn--on' : ''}`} onClick={() => setShowSMA((v) => !v)}>
          SMA 7
        </button>
        {/* Crosshair data */}
        {hc && (
          <div className="cc-xhair">
            <span className="cc-xhair__d">{hc.date}</span>
            <span>O <b>{hc.open.toFixed(2)}</b></span>
            <span>H <b className="cc-hi">{hc.high.toFixed(2)}</b></span>
            <span>L <b className="cc-lo">{hc.low.toFixed(2)}</b></span>
            <span>C <b style={{ color: hc.up ? '#39FF88' : '#FF3B5C' }}>{hc.close.toFixed(2)}</b></span>
            <span>V <b>{(hc.volume / 1e6).toFixed(1)}M</b></span>
          </div>
        )}
      </div>

      {/* ── SVG ── */}
      <div className="cc-canvas">
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid meet" className="cc-svg" onMouseMove={onMove} onMouseLeave={() => setHoverIdx(null)}>
          {/* Grid */}
          {chart?.grid.map((g, i) => (
            <g key={i}>
              <line x1={PAD.left} y1={g.y} x2={W - PAD.right} y2={g.y} stroke="#1a1a2e" strokeWidth="1" strokeDasharray={i === 0 ? '0' : '3,6'} />
              <text x={W - PAD.right + 5} y={g.y + 4} fill="#4a4a64" fontSize="9" fontFamily="IBM Plex Mono, monospace" fontWeight="500">
                {g.price.toFixed(0)}
              </text>
            </g>
          ))}

          {/* Volume */}
          {chart?.items.map((c, i) => (
            <rect key={`v${i}`} x={c.x} y={c.vy} width={c.cw} height={c.vh}
              fill={c.up ? 'rgba(57,255,136,0.12)' : 'rgba(255,59,92,0.12)'} rx="1" />
          ))}

          {/* Candles */}
          {chart?.items.map((c, i) => (
            <g key={i}>
              {i === hoverIdx && (
                <line x1={c.x + c.cw / 2} y1={PAD.top} x2={c.x + c.cw / 2} y2={H - VOL_H - 2}
                  stroke="rgba(255,212,0,0.2)" strokeWidth="1" strokeDasharray="3,5" />
              )}
              <line x1={c.x + c.cw / 2} y1={c.wt} x2={c.x + c.cw / 2} y2={c.wb}
                stroke={c.up ? '#39FF88' : '#FF3B5C'} strokeWidth="1.2" />
              <rect x={c.x} y={c.bt} width={c.cw} height={Math.max(c.bb - c.bt, 1.5)}
                fill={c.up ? '#39FF88' : '#FF3B5C'} rx="1" opacity={i === hoverIdx ? 1 : 0.85} />
            </g>
          ))}

          {/* SMA */}
          {showSMA && chart?.smaP && (
            <polyline points={chart.smaP} fill="none" stroke="#FFD400" strokeWidth="1.5" strokeLinejoin="round" opacity="0.8" />
          )}
        </svg>
      </div>

      {/* ── OHLCV footer ── */}
      <div className="cc-foot">
        <span><b>O</b> {last.open.toFixed(2)}</span>
        <span><b>H</b> {last.high.toFixed(2)}</span>
        <span><b>L</b> {last.low.toFixed(2)}</span>
        <span><b>C</b> {last.close.toFixed(2)}</span>
        <span><b>V</b> {(last.volume / 1e6).toFixed(1)}M</span>
        {showSMA && visSMA[visSMA.length - 1] != null && (
          <span className="cc-sma-v"><b>SMA7</b> {visSMA[visSMA.length - 1]?.toFixed(2)}</span>
        )}
      </div>
    </div>
  );
};

export default CandlestickChart;
