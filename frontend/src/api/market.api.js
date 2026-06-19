/**
 * api/market.api.js — Market Data API module
 *
 * Canonical name for the market history API module.
 * Replaces the old `api/marketApi.js` (which is preserved for backward compat).
 *
 * All market data fetching should import from this file going forward.
 */
import apiClient from './client';

/**
 * Fetch OHLCV candles for a given stock symbol.
 * Data is fetched from Yahoo Finance via the backend proxy and cached server-side.
 *
 * @param {string} symbol - NSE symbol without .NS suffix (e.g. "INFY")
 * @param {string} start  - Start date in YYYY-MM-DD format
 * @param {string} end    - End date in YYYY-MM-DD format
 * @returns {Promise<Array<{ date: string, open: number, high: number, low: number, close: number, volume: number }>>}
 */
export async function fetchMarketHistory(symbol, start, end) {
  const res = await apiClient.get('/api/market/history', {
    params: { symbol, start, end },
  });

  if (!Array.isArray(res.data)) {
    throw new Error('Unexpected response format from market API');
  }

  return res.data;
}

/**
 * Fetch the list of tradeable stock symbols.
 * @returns {Promise<Array<string>>}
 */
export async function fetchSymbols() {
  const res = await apiClient.get('/api/market/symbols');
  return res.data;
}

/**
 * Fetch the latest candle (current price snapshot) for a symbol.
 * @param {string} symbol
 * @returns {Promise<{ date, open, high, low, close, volume }>}
 */
export async function fetchLatestCandle(symbol) {
  const res = await apiClient.get(`/api/market/latest?symbol=${encodeURIComponent(symbol)}`);
  return res.data;
}
