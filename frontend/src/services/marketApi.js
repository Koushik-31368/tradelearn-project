// src/services/marketApi.js
import { backendUrl } from '../utils/api';

/**
 * Fetches OHLCV candles for a given NSE symbol from the Spring Boot backend.
 * Data is served from local classpath JSON files — no external API dependencies.
 *
 * @param {string} symbol - NSE symbol without .NS suffix (e.g. "INFY")
 * @returns {Promise<Array<{ time, open, high, low, close, volume }>>}
 * @throws  {Error} if the fetch fails or the backend returns an error body
 */
export async function fetchMarketHistory(symbol) {
  const path = `/api/candles/${encodeURIComponent(symbol)}`;

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
