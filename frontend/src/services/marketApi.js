// src/services/marketApi.js
import { backendUrl } from '../utils/api';

/**
 * Fetches 5-minute historical OHLCV candles for a given NSE symbol
 * from the Spring Boot backend which proxies Yahoo Finance.
 *
 * @param {string} symbol - NSE symbol without .NS suffix (e.g. "INFY", "RELIANCE")
 * @returns {Promise<Array<{ time, open, high, low, close, volume }>>}
 * @throws {Error} if the request fails or the backend returns an error
 */
export async function fetchMarketHistory(symbol) {
  const res = await fetch(backendUrl(`/api/market/history?symbol=${encodeURIComponent(symbol)}`));

  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      if (body?.error) msg = body.error;
    } catch (_) {
      // ignore JSON parse error
    }
    throw new Error(msg);
  }

  const data = await res.json();

  if (!Array.isArray(data)) {
    throw new Error('Unexpected response format from market API');
  }

  return data;
}
