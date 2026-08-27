// src/features/simulator/utils/simulatorData.js
// Daily-seeded demo data generator for the simulator

const STOCKS = [
  { symbol: 'TCS',        name: 'Tata Consultancy',     sector: 'IT',        basePrice: 3650.50 },
  { symbol: 'INFY',       name: 'Infosys Ltd',          sector: 'IT',        basePrice: 1452.30 },
  { symbol: 'RELIANCE',   name: 'Reliance Industries',  sector: 'Energy',    basePrice: 2456.75 },
  { symbol: 'HDFCBANK',   name: 'HDFC Bank',            sector: 'Banking',   basePrice: 1589.90 },
  { symbol: 'ICICIBANK',  name: 'ICICI Bank',           sector: 'Banking',   basePrice: 945.20  },
  { symbol: 'WIPRO',      name: 'Wipro Ltd',            sector: 'IT',        basePrice: 432.65  },
  { symbol: 'SBIN',       name: 'State Bank of India',  sector: 'Banking',   basePrice: 578.40  },
  { symbol: 'BHARTIARTL', name: 'Bharti Airtel',        sector: 'Telecom',   basePrice: 1234.50 },
  { symbol: 'ITC',        name: 'ITC Ltd',              sector: 'FMCG',      basePrice: 456.30  },
  { symbol: 'KOTAKBANK',  name: 'Kotak Mahindra Bank',  sector: 'Banking',   basePrice: 1789.25 },
  { symbol: 'LT',         name: 'Larsen & Toubro',      sector: 'Infra',     basePrice: 3456.80 },
  { symbol: 'AXISBANK',   name: 'Axis Bank',            sector: 'Banking',   basePrice: 1023.45 },
  { symbol: 'HINDUNILVR', name: 'Hindustan Unilever',   sector: 'FMCG',      basePrice: 2567.90 },
  { symbol: 'MARUTI',     name: 'Maruti Suzuki',        sector: 'Auto',      basePrice: 10245.60},
  { symbol: 'TATASTEEL',  name: 'Tata Steel',           sector: 'Metals',    basePrice: 134.75  },
];

// ── Deterministic seeded RNG (mulberry32) ──────────────────────────────────
function mulberry32(seed) {
  return function () {
    let t = (seed += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function dateSeed(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}

function todayKey() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const LS_KEY           = 'tradelearn_sim_prices';
const LS_DAY_KEY       = 'tradelearn_sim_day';
const LS_PORTFOLIO_KEY = 'tradelearn_sim_portfolio';
const LS_HISTORY_KEY   = 'tradelearn_sim_history';

// ── Daily stock prices ─────────────────────────────────────────────────────
export function getDailyStocks() {
  const today = todayKey();
  const cached = localStorage.getItem(LS_DAY_KEY);

  if (cached === today) {
    try {
      const stored = JSON.parse(localStorage.getItem(LS_KEY));
      if (stored && stored.length === STOCKS.length) return stored;
    } catch (_) { /* regenerate */ }
  }

  const rng = mulberry32(dateSeed(today));
  const stocks = STOCKS.map((s) => {
    const changePct = (rng() - 0.5) * 0.05;
    const price = +(s.basePrice * (1 + changePct)).toFixed(2);
    const change = +(changePct * 100).toFixed(2);
    return { ...s, price, change };
  });

  localStorage.setItem(LS_KEY, JSON.stringify(stocks));
  localStorage.setItem(LS_DAY_KEY, today);
  return stocks;
}

// ── Candle history generator (90-day OHLCV, deterministic) ─────────────────
export function generateCandleHistory(symbol, days = 90) {
  const stock = STOCKS.find((s) => s.symbol === symbol);
  if (!stock) return [];

  const today  = todayKey();
  const rng    = mulberry32(dateSeed(today + symbol));
  const candles = [];
  let prevClose = stock.basePrice * (0.90 + rng() * 0.20);

  for (let i = days; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    // Skip weekends
    if (d.getDay() === 0 || d.getDay() === 6) continue;
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

    const dayChange = (rng() - 0.47) * 0.04; // slight upward bias
    const open  = prevClose;
    const close = +(open * (1 + dayChange)).toFixed(2);
    const high  = +(Math.max(open, close) * (1 + rng() * 0.018)).toFixed(2);
    const low   = +(Math.min(open, close) * (1 - rng() * 0.018)).toFixed(2);
    const volume = Math.floor(800_000 + rng() * 9_200_000);

    candles.push({ date: dateStr, open: +open.toFixed(2), high, low, close, volume });
    prevClose = close;
  }
  return candles;
}

// ── Scenario candle generator for educational patterns ─────────────────────
export const SCENARIOS = [
  { key: 'trending',      label: '📈 Bull Run',         desc: 'Strong uptrend with minor pullbacks' },
  { key: 'crash',         label: '📉 Market Crash',      desc: 'Sharp sell-off followed by dead-cat bounce' },
  { key: 'breakout',      label: '🚀 Breakout',          desc: 'Consolidation then explosive breakout' },
  { key: 'consolidation', label: '↔️  Sideways Range',   desc: 'Stock trapped in a tight range' },
  { key: 'recovery',      label: '🔄 V-Recovery',        desc: 'Sharp drop followed by full recovery' },
];

export function generateScenarioCandleHistory(symbol, scenario = 'trending', days = 90) {
  const stock = STOCKS.find((s) => s.symbol === symbol);
  const basePrice = stock ? stock.basePrice : 1000;
  const rng = mulberry32(dateSeed(symbol + scenario));
  const candles = [];
  let price = basePrice * (0.88 + rng() * 0.24);

  for (let i = days; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    if (d.getDay() === 0 || d.getDay() === 6) continue;
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

    const progress = 1 - i / days; // 0 → 1 over time
    let drift = 0;
    let noise = (rng() - 0.5) * 0.025;

    switch (scenario) {
      case 'trending':      drift = 0.008;  break;
      case 'crash':         drift = progress < 0.5 ? -0.018 : 0.004; break;
      case 'breakout':      drift = progress < 0.6 ? 0.001 : 0.018;  break;
      case 'consolidation': drift = Math.sin(progress * Math.PI * 6) * 0.003; noise *= 0.4; break;
      case 'recovery':      drift = progress < 0.4 ? -0.015 : 0.020; break;
      default:              drift = 0.002;
    }

    const open  = price;
    const close = +(open * (1 + drift + noise)).toFixed(2);
    const high  = +(Math.max(open, close) * (1 + rng() * 0.014)).toFixed(2);
    const low   = +(Math.min(open, close) * (1 - rng() * 0.014)).toFixed(2);
    const volume = Math.floor(600_000 + rng() * 8_000_000);

    candles.push({ date: dateStr, open: +open.toFixed(2), high, low, close, volume });
    price = close;
  }
  return candles;
}

// ── Equity curve ───────────────────────────────────────────────────────────
export function generateEquityCurve() {
  const today = todayKey();
  const rng = mulberry32(dateSeed(today + 'equity'));
  const points = [];
  let value = 1_000_000;

  for (let i = 30; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const dateStr = `${String(d.getDate()).padStart(2, '0')} ${d.toLocaleString('default', { month: 'short' })}`;
    value = +(value * (1 + (rng() - 0.47) * 0.025)).toFixed(2);
    points.push({ date: dateStr, value });
  }
  return points;
}

// ── SMA calculator ─────────────────────────────────────────────────────────
export function computeSMA(data, period) {
  const result = [];
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) { result.push(null); continue; }
    let sum = 0;
    for (let j = i - period + 1; j <= i; j++) sum += data[j].close;
    result.push(+(sum / period).toFixed(2));
  }
  return result;
}

// ── Portfolio management ───────────────────────────────────────────────────
function defaultPortfolio() {
  return { cash: 1_000_000, holdings: {}, totalInvested: 0 };
}

export function getPortfolio() {
  try {
    const raw = localStorage.getItem(LS_PORTFOLIO_KEY);
    return raw ? JSON.parse(raw) : defaultPortfolio();
  } catch { return defaultPortfolio(); }
}

export function savePortfolio(p) { localStorage.setItem(LS_PORTFOLIO_KEY, JSON.stringify(p)); }

export function resetPortfolio() {
  localStorage.removeItem(LS_PORTFOLIO_KEY);
  localStorage.removeItem(LS_HISTORY_KEY);
  return defaultPortfolio();
}

export function getTradeHistory() {
  try {
    const raw = localStorage.getItem(LS_HISTORY_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch { return []; }
}

export function saveTradeHistory(h) { localStorage.setItem(LS_HISTORY_KEY, JSON.stringify(h)); }

// ── Trade execution ────────────────────────────────────────────────────────
export function executeDemoTrade({ symbol, price, quantity, type, journalId }) {
  const portfolio = getPortfolio();
  const history   = getTradeHistory();
  const total     = price * quantity;
  const now       = new Date().toISOString();
  let pnl = null, closedJournalId = null;

  switch (type) {
    case 'BUY': {
      if (total > portfolio.cash) return { success: false, message: 'Insufficient funds' };
      portfolio.cash -= total;
      const existing = portfolio.holdings[symbol] || { qty: 0, avgPrice: 0, journalId };
      const newQty   = existing.qty + quantity;
      existing.avgPrice = +((existing.avgPrice * existing.qty + total) / newQty).toFixed(2);
      existing.qty  = newQty;
      portfolio.holdings[symbol] = existing;
      break;
    }
    case 'SELL': {
      const h = portfolio.holdings[symbol];
      if (!h || h.qty < quantity) return { success: false, message: 'Insufficient holdings' };
      portfolio.cash += total;
      pnl = +((price - h.avgPrice) * quantity).toFixed(2);
      closedJournalId = h.journalId;
      h.qty -= quantity;
      if (h.qty === 0) delete portfolio.holdings[symbol];
      break;
    }
    case 'SHORT': {
      portfolio.cash += total;
      const existing = portfolio.holdings[symbol] || { qty: 0, avgPrice: 0, journalId };
      existing.qty -= quantity;
      existing.avgPrice = price;
      portfolio.holdings[symbol] = existing;
      break;
    }
    case 'COVER': {
      const h = portfolio.holdings[symbol];
      if (!h || h.qty >= 0) return { success: false, message: 'No short position to cover' };
      portfolio.cash -= total;
      pnl = +((h.avgPrice - price) * quantity).toFixed(2);
      closedJournalId = h.journalId;
      h.qty += quantity;
      if (h.qty === 0) delete portfolio.holdings[symbol];
      break;
    }
    default:
      return { success: false, message: 'Invalid trade type' };
  }

  history.unshift({ id: Date.now(), symbol, type, quantity, price, total: +total.toFixed(2), date: now });
  savePortfolio(portfolio);
  saveTradeHistory(history);

  // Async PnL sync — fire-and-forget, never blocks the UI
  if (pnl !== null && closedJournalId) {
    const status = pnl > 0 ? 'WIN' : pnl < 0 ? 'LOSS' : 'BREAKEVEN';
    fetch(`/api/journals/close/${closedJournalId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pnl, outcomeStatus: status }),
    }).catch(() => { /* graceful degradation — PnL sync is best-effort */ });
  }

  return { success: true, message: `${type} ${quantity} ${symbol} @ ₹${price.toFixed(2)}`, portfolio };
}
