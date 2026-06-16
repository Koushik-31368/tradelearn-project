// src/services/marketApi.js
import { backendUrl } from '../utils/api';

/**
 * Fetches OHLCV candles for a given symbol from the Spring Boot backend.
 * Data is fetched from Yahoo Finance via the backend proxy and cached.
 *
 * @param {string} symbol - NSE symbol without .NS suffix (e.g. "INFY")
 * @param {string} start - Start date in YYYY-MM-DD format
 * @param {string} end - End date in YYYY-MM-DD format
 * @returns {Promise<Array<{ date, open, high, low, close, volume }>>}
 */
export async function fetchMarketHistory(symbol, start, end) {
  const path = `/api/market/history?symbol=${encodeURIComponent(symbol)}&start=${start}&end=${end}`;

  const res = await fetch(backendUrl(path));

  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      if (body?.error) msg = body.error;
    } catch (_) { /* ignore */ }
    throw new Error(msg);
  }

  const data = await res.json();

  if (!Array.isArray(data)) {
    throw new Error('Unexpected response format from market API');
  }

  return data;
}
