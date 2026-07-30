// e2e/helpers/auth.js
// ─────────────────────────────────────────────────────────────────────────────
// Shared auth helpers — fill forms, submit, wait for navigation.
// Black-box only: all interaction via browser UI.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Generate a unique test email so repeated runs don't collide on the throwaway DB.
 */
function uniqueEmail(prefix = 'user') {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}@e2e.test`;
}

/**
 * Register a new account via the /register UI form.
 * Returns when the URL has moved away from /register (success redirect).
 *
 * @param {import('@playwright/test').Page} page
 * @param {object} opts
 * @param {string} opts.email
 * @param {string} opts.username
 * @param {string} opts.password
 */
async function registerUser(page, { email, username, password }) {
  await page.goto('/register');

  await page.fill('input[type="email"]', email);
  await page.fill('input[type="text"]', username);
  await page.fill('input[type="password"]', password);

  // The "agree" checkbox must be checked before the button is enabled
  const checkbox = page.locator('input[type="checkbox"]');
  if (await checkbox.isVisible({ timeout: 2_000 }).catch(() => false)) {
    await checkbox.check();
  }

  await page.click('button[type="submit"]');

  // Wait for redirect away from /register.
  // FIX: use a strict regex that doesn't accidentally match the current /register URL.
  // The app navigates to /learn (which has no route) or /multiplayer.
  await page.waitForURL((url) => !url.pathname.startsWith('/register'), { timeout: 20_000 });
}

/**
 * Log in via the /login UI form.
 * Waits for redirect away from /login to confirm the login succeeded.
 *
 * FIX: Previous version used regex /\/(multiplayer|profile|\/?)/  which contained
 *      \/?  (optional slash) as an alternative — this matched ANY URL that contains
 *      a "/" character, including "/login" itself, causing the helper to return
 *      immediately before login completed. Now uses a URL predicate instead.
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

  // Wait until we navigate AWAY from /login — indicates successful login redirect.
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20_000 });
}

/**
 * Returns true if the page is in a logged-in state (Logout button visible).
 *
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<boolean>}
 */
async function isLoggedIn(page) {
  const logoutBtn = page.locator('.logout-button, button:has-text("Logout")');
  return await logoutBtn.isVisible({ timeout: 5_000 }).catch(() => false);
}

/**
 * Collect all uncaught console errors from a page.
 * Call this before navigation then check the array after.
 *
 * KNOWN NOISE: /api/auth/refresh returns 401 on every page load for unauthenticated
 * users (no cookie present). Chrome logs this as "Failed to load resource: 400".
 * This is EXPECTED behaviour and is filtered out automatically.
 *
 * @param {import('@playwright/test').Page} page
 * @returns {{ messages: string[], off: () => void }}
 */
function collectConsoleErrors(page) {
  const messages = [];
  const handler = (msg) => {
    if (msg.type() === 'error') {
      const text = msg.text();
      // Filter expected noise:
      // - React dev warnings
      // - Auth/refresh 401 (unauthenticated page loads — expected)
      // - Generic "400/401" resource load failures (usually the above)
      if (
        text.includes('Warning:') ||
        text.includes('Download the React DevTools') ||
        (text.includes('Failed to load resource') &&
          (text.includes('400') || text.includes('401')))
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

module.exports = { uniqueEmail, registerUser, loginUser, isLoggedIn, collectConsoleErrors };
