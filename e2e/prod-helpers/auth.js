// e2e/prod-helpers/auth.js
// ─────────────────────────────────────────────────────────────────────────────
// Shared auth helpers for PRODUCTION tests.
// Key differences from local helpers:
//  - All test accounts prefixed "e2e_test_" for easy cleanup
//  - Every created account is logged to e2e/prod-test-accounts.log
//  - Longer waits baked in (Render network latency vs localhost)
// ─────────────────────────────────────────────────────────────────────────────

const fs = require('fs');
const path = require('path');

const LOG_FILE = path.join(__dirname, '..', 'prod-test-accounts.log');

const TIMESTAMP = Date.now(); // Shared timestamp for this test run

/**
 * Generate a unique test email with e2e_test_ prefix.
 * Format: e2e_test_<prefix>_<timestamp>_<random>@e2e.prod.test
 * The @e2e.prod.test domain makes these unmistakable in the DB.
 */
function uniqueEmail(prefix = 'user') {
  const rand = Math.random().toString(36).slice(2, 7);
  return `e2e_test_${prefix}_${TIMESTAMP}_${rand}@e2e.prod.test`;
}

/**
 * Generate a unique username with e2e_test_ prefix.
 */
function uniqueUsername(prefix = 'user') {
  const rand = Math.random().toString(36).slice(2, 6);
  return `e2e_test_${prefix}_${TIMESTAMP}_${rand}`;
}

/**
 * Log a created account to prod-test-accounts.log for cleanup.
 * Each line: ISO_TIMESTAMP | email | username | context
 */
function logCreatedAccount(email, username, context = '') {
  const line = `${new Date().toISOString()} | ${email} | ${username} | ${context}\n`;
  try {
    fs.appendFileSync(LOG_FILE, line, 'utf8');
    console.log(`[prod-auth] 📝 Logged test account: ${email} (${username})`);
  } catch (err) {
    console.warn(`[prod-auth] Warning: Could not write to ${LOG_FILE}: ${err.message}`);
  }
}

/**
 * Register a new account via the /register UI form.
 * Returns when the URL has moved away from /register (success redirect).
 * Also logs the account to prod-test-accounts.log.
 *
 * @param {import('@playwright/test').Page} page
 * @param {object} opts
 * @param {string} opts.email
 * @param {string} opts.username
 * @param {string} opts.password
 * @param {string} [opts.context]  — optional label for the log file
 */
async function registerUser(page, { email, username, password, context = 'unknown-test' }) {
  await page.goto('/register');

  await page.fill('input[type="email"]', email);
  await page.fill('input[type="text"]', username);
  await page.fill('input[type="password"]', password);

  // Agree checkbox (if present)
  const checkbox = page.locator('input[type="checkbox"]');
  if (await checkbox.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await checkbox.check();
  }

  await page.click('button[type="submit"]');

  // Wait for redirect away from /register (success signal)
  // Extended to 30s for prod network latency + backend registration time
  await page.waitForURL((url) => !url.pathname.startsWith('/register'), { timeout: 30_000 });

  // Log the account immediately after successful registration
  logCreatedAccount(email, username, context);
}

/**
 * Log in via the /login UI form.
 * Waits for redirect away from /login to confirm success.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} email
 * @param {string} password
 */
async function loginUser(page, email, password) {
  await page.goto('/login');

  await page.fill('input[type="email"]', email);
  await page.fill('input[type="password"]', password);
  await page.click('button[type="submit"]');

  // Wait until we navigate AWAY from /login — indicates successful login
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30_000 });
}

/**
 * Returns true if the page is in a logged-in state (Logout button visible).
 */
async function isLoggedIn(page) {
  const logoutBtn = page.locator('.logout-button, button:has-text("Logout")');
  return await logoutBtn.isVisible({ timeout: 8_000 }).catch(() => false);
}

/**
 * Collect all uncaught console errors from a page.
 * Filters known production noise (React warnings, auth/refresh 401s, CORS preflight noise).
 *
 * @param {import('@playwright/test').Page} page
 * @returns {{ messages: string[], off: () => void }}
 */
function collectConsoleErrors(page) {
  const messages = [];
  const handler = (msg) => {
    if (msg.type() === 'error') {
      const text = msg.text();
      // Filter expected noise in production:
      if (
        text.includes('Warning:') ||
        text.includes('Download the React DevTools') ||
        // Unauthenticated refresh 401 (expected on every page load)
        (text.includes('Failed to load resource') && (text.includes('400') || text.includes('401'))) ||
        // CORS preflight failures for WebSocket on some browsers
        text.includes('net::ERR_FAILED') && text.includes('sockjs')
      ) {
        return;
      }
      messages.push(text);
    }
  };
  page.on('console', handler);
  return {
    messages,
    off: () => page.off('console', handler),
  };
}

/**
 * Measure how long a page takes to become interactive (DOMContentLoaded).
 * Returns milliseconds. Flags (via console.warn) if > threshold.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} path  — route path for logging
 * @param {number} warnThresholdMs  — warn if load takes longer than this
 * @returns {Promise<number>} elapsed ms
 */
async function measurePageLoad(page, path, warnThresholdMs = 5_000) {
  const start = Date.now();
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  const elapsed = Date.now() - start;

  if (elapsed > warnThresholdMs) {
    console.warn(
      `[prod-perf] ⚠️  SLOW PAGE LOAD: ${path} took ${elapsed}ms ` +
      `(threshold: ${warnThresholdMs}ms). May indicate a real prod issue.`
    );
  } else {
    console.log(`[prod-perf] ✓ ${path} loaded in ${elapsed}ms`);
  }
  return elapsed;
}

module.exports = {
  uniqueEmail,
  uniqueUsername,
  logCreatedAccount,
  registerUser,
  loginUser,
  isLoggedIn,
  collectConsoleErrors,
  measurePageLoad,
  TIMESTAMP,
};
