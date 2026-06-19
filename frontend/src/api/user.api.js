/**
 * api/user.api.js — User / Account API module
 *
 * All HTTP calls related to the authenticated user's account:
 * profile updates, daily check-in, quests, achievements.
 */
import apiClient from './client';

/**
 * Trigger a daily check-in for the authenticated user.
 * Grants any daily rewards and resets the daily quest progress.
 * @returns {Promise<object>}
 */
export async function dailyCheckin() {
  const res = await apiClient.post('/api/user/daily-checkin');
  return res.data;
}

/**
 * Fetch the authenticated user's daily quests.
 * @returns {Promise<Array<{ id, title, description, progress, goal, completed }>>}
 */
export async function fetchDailyQuests() {
  const res = await apiClient.get('/api/quests/daily');
  return res.data;
}

/**
 * Fetch the authenticated user's weekly challenges.
 * @returns {Promise<Array<{ id, title, description, progress, goal, completed }>>}
 */
export async function fetchWeeklyChallenges() {
  const res = await apiClient.get('/api/quests/weekly');
  return res.data;
}

/**
 * Fetch the authenticated user's achievements.
 * @returns {Promise<Array<{ id, name, description, icon, unlockedAt }>>}
 */
export async function fetchAchievements() {
  const res = await apiClient.get('/api/achievements/user');
  return res.data;
}

/**
 * Fetch the current user's account details (username, email, rating, etc.).
 * @returns {Promise<object>}
 */
export async function getMe() {
  const res = await apiClient.get('/api/user/me');
  return res.data;
}

/**
 * Update the current user's profile (display name, avatar, etc.).
 * @param {object} data — fields to update
 * @returns {Promise<object>}
 */
export async function updateProfile(data) {
  const res = await apiClient.put('/api/user/profile', data);
  return res.data;
}

/**
 * Fetch the readiness score for the authenticated user.
 * Used on the simulator and analytics pages.
 * @returns {Promise<{ score: number, breakdown: object }>}
 */
export async function getReadinessScore() {
  const res = await apiClient.get('/api/analytics/readiness');
  return res.data;
}
