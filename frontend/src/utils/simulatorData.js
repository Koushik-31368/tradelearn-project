// src/utils/simulatorData.js
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

// Deterministic seeded random number generator (mulberry32)
function mulberry32(seed) {
  return function () {
    let t = (seed += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Produce a numeric seed from today's date string
function dateSeed(dateStr) {
  let hash = 0;
  for (let i = 0; i < dateStr.length; i++) {
    hash = (hash << 5) - hash + dateStr.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}

// Get today's date key
function todayKey() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const LS_KEY = 'tradelearn_sim_prices';
const LS_DAY_KEY = 'tradelearn_sim_day';
const LS_PORTFOLIO_KEY = 'tradelearn_sim_portfolio';
const LS_HISTORY_KEY = 'tradelearn_sim_history';

/**
 * Generate today's stock prices using a daily seed.
 * Prices fluctuate ±2.5% from the base price, seeded by the date.
 * Cached in localStorage for the day.
 */
export function getDailyStocks() {
  const today = todayKey();
  const cached = localStorage.getItem(LS_DAY_KEY);

  if (cached === today) {
    try {
      const stored = JSON.parse(localStorage.getItem(LS_KEY));
      if (stored && stored.length === STOCKS.length) return stored;
    } catch (_) { /* regenerate */ }
  }

  const seed = dateSeed(today);
  const rng = mulberry32(seed);

  const stocks = STOCKS.map((s) => {
    const changePct = (rng() - 0.5) * 0.05; // ±2.5%
    const price = +(s.basePrice * (1 + changePct)).toFixed(2);
    const change = +((changePct) * 100).toFixed(2);
    return { ...s, price, change };
  });

  localStorage.setItem(LS_KEY, JSON.stringify(stocks));
  localStorage.setItem(LS_DAY_KEY, today);
  return stocks;
}

/**
 * Generate 30-day OHLC candle history for a given stock.
 * Uses a deterministic seed per stock + day offset.
 */
export function generateCandleHistory(symbol, days = 30) {
  const stock = STOCKS.find((s) => s.symbol === symbol);
  if (!stock) return [];

  const today = todayKey();
  const baseSeed = dateSeed(today + symbol);
  const rng = mulberry32(baseSeed);

  const candles = [];
  let prevClose = stock.basePrice * (0.92 + rng() * 0.16); // start within ±8%

  for (let i = days; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

    const dayChange = (rng() - 0.48) * 0.04; // slight upward bias
    const open = prevClose;
    const close = +(open * (1 + dayChange)).toFixed(2);
    const high = +(Math.max(open, close) * (1 + rng() * 0.015)).toFixed(2);
    const low = +(Math.min(open, close) * (1 - rng() * 0.015)).toFixed(2);
    const volume = Math.floor(500000 + rng() * 4500000);

    candles.push({ date: dateStr, open: +open.toFixed(2), high, low, close, volume });
    prevClose = close;
  }

  return candles;
}

/**
 * Generate equity curve data (portfolio value over 30 days).
 */
export function generateEquityCurve() {
  const today = todayKey();
  const rng = mulberry32(dateSeed(today + 'equity'));
  const points = [];
  let value = 1000000;

  for (let i = 30; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const dateStr = `${String(d.getDate()).padStart(2, '0')} ${d.toLocaleString('default', { month: 'short' })}`;
    value = +(value * (1 + (rng() - 0.47) * 0.025)).toFixed(2);
    points.push({ date: dateStr, value });
  }
  return points;
}

/**
 * Simple SMA calculator.
 */
export function computeSMA(data, period) {
  const result = [];
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      result.push(null);
    } else {
      let sum = 0;
      for (let j = i - period + 1; j <= i; j++) {
        sum += data[j].close;
      }
      result.push(+(sum / period).toFixed(2));
    }
  }
  return result;
}


/* ---- Local Portfolio Management ---- */

function defaultPortfolio() {
  return {
    cash: 1000000,
    holdings: {},       // { symbol: { qty, avgPrice } }
    totalInvested: 0,
  };
}

export function getPortfolio() {
  try {
    const raw = localStorage.getItem(LS_PORTFOLIO_KEY);
    return raw ? JSON.parse(raw) : defaultPortfolio();
  } catch {
    return defaultPortfolio();
  }
}

export function savePortfolio(portfolio) {
  localStorage.setItem(LS_PORTFOLIO_KEY, JSON.stringify(portfolio));
}

export function getTradeHistory() {
  try {
    const raw = localStorage.getItem(LS_HISTORY_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

export function saveTradeHistory(history) {
  localStorage.setItem(LS_HISTORY_KEY, JSON.stringify(history));
}

/**
 * Execute a trade (buy/sell/short/cover) and update local portfolio + history.
 * Returns { success, message, portfolio }
 */
export function executeDemoTrade({ symbol, price, quantity, type }) {
  const portfolio = getPortfolio();
  const history = getTradeHistory();
  const total = price * quantity;
  const now = new Date().toISOString();

  switch (type) {
    case 'BUY': {
      if (total > portfolio.cash) return { success: false, message: 'Insufficient funds' };
      portfolio.cash -= total;
      const existing = portfolio.holdings[symbol] || { qty: 0, avgPrice: 0 };
      const newQty = existing.qty + quantity;
      existing.avgPrice = +((existing.avgPrice * existing.qty + total) / newQty).toFixed(2);
      existing.qty = newQty;
      portfolio.holdings[symbol] = existing;
      break;
    }
    case 'SELL': {
      const holding = portfolio.holdings[symbol];
      if (!holding || holding.qty < quantity) return { success: false, message: 'Insufficient holdings' };
      portfolio.cash += total;
      holding.qty -= quantity;
      if (holding.qty === 0) delete portfolio.holdings[symbol];
      break;
    }
    case 'SHORT': {
      // Simplified: credit cash, create negative holding
      portfolio.cash += total;
      const existing = portfolio.holdings[symbol] || { qty: 0, avgPrice: 0 };
      const newQty = existing.qty - quantity;
      existing.avgPrice = price;
      existing.qty = newQty;
      portfolio.holdings[symbol] = existing;
      break;
    }
    case 'COVER': {
      const holding = portfolio.holdings[symbol];
      if (!holding || holding.qty >= 0) return { success: false, message: 'No short position to cover' };
      portfolio.cash -= total;
      holding.qty += quantity;
      if (holding.qty === 0) delete portfolio.holdings[symbol];
      break;
    }
    default:
      return { success: false, message: 'Invalid trade type' };
  }

  history.unshift({ id: Date.now(), symbol, type, quantity, price, total: +total.toFixed(2), date: now });
  savePortfolio(portfolio);
  saveTradeHistory(history);

  return { success: true, message: `${type} ${quantity} ${symbol} @ ₹${price.toFixed(2)}`, portfolio };
}
