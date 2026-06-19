/**
 * api/game.api.js — Game / Match API module
 *
 * All REST calls for match lifecycle: create, join, delete, fetch.
 * WebSocket trade submission still goes through the STOMP connection directly.
 */
import apiClient from './client';

/**
 * Fetch all open (WAITING) games.
 * @returns {Promise<Array<object>>}
 */
export async function getOpenMatches() {
  const res = await apiClient.get('/api/match/open');
  return res.data;
}

/**
 * Fetch active (in-progress) games.
 * @returns {Promise<Array<object>>}
 */
export async function getActiveMatches() {
  const res = await apiClient.get('/api/match/active');
  return res.data;
}

/**
 * Fetch a single match by ID.
 * @param {number|string} gameId
 * @returns {Promise<object>}
 */
export async function getMatch(gameId) {
  const res = await apiClient.get(`/api/match/${gameId}`);
  return res.data;
}

/**
 * Create a new custom game.
 * @param {{ stockSymbol: string, durationMinutes: number, startingBalance: number }} data
 * @returns {Promise<object>}
 */
export async function createMatch(data) {
  const res = await apiClient.post('/api/match', data);
  return res.data;
}

/**
 * Join an existing open game.
 * @param {number|string} gameId
 * @returns {Promise<object>}
 */
export async function joinMatch(gameId) {
  const res = await apiClient.post(`/api/match/${gameId}/join`);
  return res.data;
}

/**
 * Cancel (delete) a game the current user created.
 * @param {number|string} gameId
 */
export async function deleteMatch(gameId) {
  const res = await apiClient.delete(`/api/match/${gameId}`);
  return res.data;
}

/**
 * Request a rematch after a finished game.
 * @param {number|string} gameId
 */
export async function requestRematch(gameId) {
  const res = await apiClient.post(`/api/match/${gameId}/rematch`);
  return res.data;
}

/**
 * Fetch match result / summary for a finished game.
 * @param {number|string} gameId
 * @returns {Promise<object>}
 */
export async function getMatchResult(gameId) {
  const res = await apiClient.get(`/api/match/${gameId}/result`);
  return res.data;
}

/**
 * Fetch the current user's match history.
 * @returns {Promise<Array<object>>}
 */
export async function getUserMatches() {
  const res = await apiClient.get('/api/match/user');
  return res.data;
}

// ── Ranked Matchmaking ────────────────────────────────────────────────────────

/**
 * Join the ranked matchmaking queue.
 * Returns immediately: status='MATCHED' (with gameId) or status='QUEUED'.
 * @returns {Promise<{ status: string, gameId?: number }>}
 */
export async function joinMatchmakingQueue() {
  const res = await apiClient.post('/api/matchmaking/queue');
  return res.data;
}

/**
 * Leave the ranked matchmaking queue.
 */
export async function leaveMatchmakingQueue() {
  const res = await apiClient.delete('/api/matchmaking/queue');
  return res.data;
}
