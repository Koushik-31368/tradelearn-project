/**
 * api/leaderboard.api.js — Leaderboard & Profile API module
 *
 * All HTTP calls for rankings, tier filters, user profiles, and match history.
 */
import apiClient from './client';

/**
 * Fetch the full leaderboard (all users, ordered by rating desc).
 * @returns {Promise<Array<{ id, username, rating, rank, wins, losses, draws }>>}
 */
export async function getLeaderboard() {
  const res = await apiClient.get('/api/users/leaderboard');
  return res.data;
}

/**
 * Fetch the top-10 leaderboard.
 * @returns {Promise<Array<object>>}
 */
export async function getTop10Leaderboard() {
  const res = await apiClient.get('/api/users/leaderboard/top10');
  return res.data;
}

/**
 * Fetch leaderboard filtered by tier (e.g. 'bronze', 'silver', 'gold').
 * @param {string} tierName
 * @returns {Promise<Array<object>>}
 */
export async function getLeaderboardByTier(tierName) {
  const res = await apiClient.get(`/api/users/leaderboard/tier/${encodeURIComponent(tierName)}`);
  return res.data;
}

/**
 * Fetch the practice leaderboard (simulator/practice mode rankings).
 * @returns {Promise<Array<object>>}
 */
export async function getPracticeLeaderboard() {
  const res = await apiClient.get('/api/practice/leaderboard');
  return res.data;
}

/**
 * Fetch a user's full profile: stats, recent matches, rank.
 * @param {number|string} userId
 * @returns {Promise<{
 *   userId, username, rating, rank, wins, losses, draws,
 *   totalFinished, avgDrawdown, avgAccuracy, avgScore,
 *   recentMatches: Array
 * }>}
 */
export async function getUserProfile(userId) {
  const res = await apiClient.get(`/api/users/${userId}/profile`);
  return res.data;
}

/**
 * Fetch the authenticated user's match history.
 * @returns {Promise<Array<object>>}
 */
export async function getMatchHistory() {
  const res = await apiClient.get('/api/match/user');
  return res.data;
}
