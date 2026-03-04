// src/services/marketApi.js
import { backendUrl } from '../utils/api';

/**
 * Fetches OHLCV candles for a given NSE symbol from the Spring Boot backend
 * which proxies Yahoo Finance.
 *
 * @param {string} symbol     - NSE symbol without .NS suffix (e.g. "INFY")
 * @param {number} [start]    - Range start in Unix epoch seconds (optional)
 * @param {number} [end]      - Range end   in Unix epoch seconds (optional)
 *
 * When both `start` and `end` are omitted the backend returns the last 5
 * trading days at 5-minute resolution (Live Data mode).
 * When timestamps are supplied the backend auto-picks the best interval
 * (5m / 1h / 1d) depending on the range age (Historical Events mode).
 *
 * @returns {Promise<Array<{ time, open, high, low, close, volume }>>}
 * @throws  {Error} if the fetch fails or the backend returns an error body
 */
export async function fetchMarketHistory(symbol, start, end) {
  let path = `/api/market/history?symbol=${encodeURIComponent(symbol)}`;
  if (start != null && end != null) {
    path += `&start=${start}&end=${end}`;
  }

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
