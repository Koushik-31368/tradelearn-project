/**
 * api/api.js — API utility re-export bridge
 *
 * All feature components import their API utilities from this path:
 *   import { backendUrl, authHeaders, api, ... } from '../../../api/api'
 *
 * Re-exports from:
 *   - api/client.js — canonical axios instance + URL helpers
 *   - utils/api.js  — legacy convenience wrappers (dailyCheckin, fetchDailyQuests, etc.)
 *
 * Long-term: migrate to the domain-specific API modules (auth.api.js, game.api.js, etc.)
 * and retire utils/api.js.
 */

// ── Core URL helpers + axios instance from canonical client ──────────────────
export {
  backendUrl,
  wsBase,
  getToken,
  authHeaders,
  jsonAuthHeaders,
  default as api,
} from './client';

// ── Legacy convenience wrappers — kept for backward compat ───────────────────
export {
  dailyCheckin,
  fetchDailyQuests,
  fetchWeeklyChallenges,
} from '../utils/api';

