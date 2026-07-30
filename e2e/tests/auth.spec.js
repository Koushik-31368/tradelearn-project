// e2e/tests/auth.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// BLACK-BOX auth flow tests — interact only through browser UI.
// Priority #1: covers the httpOnly-cookie silent-refresh architecture.
//
// FIXED:
//  - Removed .auth-msg.success assertions (message appears, then component unmounts
//    too quickly; the redirect itself is the reliable signal of success)
//  - Fixed loginUser() helper regex bug (now uses URL predicate in helpers/auth.js)
//  - auth test 6 now accepts EITHER redirect OR inline login prompt
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const { uniqueEmail, registerUser, loginUser, isLoggedIn, collectConsoleErrors } = require('../helpers/auth');

let testEmail, testUsername, testPassword;

test.beforeAll(() => {
  testEmail    = uniqueEmail('auth');
  testUsername = `authuser_${Date.now()}`;
  testPassword = 'TestPass123!';
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 1: Register → logged-in state
// ─────────────────────────────────────────────────────────────────────────────
test('register a new account → lands in logged-in state', async ({ page }) => {
  await page.goto('/register');

  await page.fill('input[type="email"]', testEmail);
  await page.fill('input[type="text"]', testUsername);
  await page.fill('input[type="password"]', testPassword);

  const checkbox = page.locator('input[type="checkbox"]');
  if (await checkbox.isVisible({ timeout: 2_000 }).catch(() => false)) {
    await checkbox.check();
  }

  await page.click('button[type="submit"]');

  // Expect redirect away from /register (success signal)
  await page.waitForURL((url) => !url.pathname.startsWith('/register'), { timeout: 20_000 });

  // Expect navbar to show logged-in state (Logout button visible)
  const logoutBtn = page.locator('.logout-button, button:has-text("Logout")');
  await expect(logoutBtn).toBeVisible({ timeout: 15_000 });
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 2: Log out → protected pages redirect to /login
// ─────────────────────────────────────────────────────────────────────────────
test('log out → /simulator redirects to /login', async ({ page }) => {
  // Log in fresh using the fixed helper
  await loginUser(page, testEmail, testPassword);

  // Confirm logged in (logout button visible)
  const logoutBtn = page.locator('.logout-button, button:has-text("Logout")');
  await expect(logoutBtn).toBeVisible({ timeout: 15_000 });

  // Click logout
  await logoutBtn.click();
  // Allow logout API call + state clear
  await page.waitForTimeout(1_500);

  // Navigate to protected route
  await page.goto('/simulator');

  // Expect redirect to /login
  await expect(page).toHaveURL(/\/login/, { timeout: 20_000 });
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 3: Log back in with correct credentials → access restored
// ─────────────────────────────────────────────────────────────────────────────
test('log in with correct credentials → access restored', async ({ page }) => {
  await loginUser(page, testEmail, testPassword);

  // Confirm we left /login (successful redirect)
  await expect(page).not.toHaveURL(/\/login/, { timeout: 5_000 });

  // Confirm logged-in state in navbar
  const logoutBtn = page.locator('.logout-button, button:has-text("Logout")');
  await expect(logoutBtn).toBeVisible({ timeout: 15_000 });
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 4: Log in with wrong password → clear error, no crash
// ─────────────────────────────────────────────────────────────────────────────
test('log in with wrong password → clear error shown, no silent failure', async ({ page }) => {
  await page.goto('/login');

  await page.fill('input[type="email"]', testEmail);
  await page.fill('input[type="password"]', 'WRONG_PASSWORD_xyz999');
  await page.click('button[type="submit"]');

  // Should stay on /login — allow time for API response
  await page.waitForTimeout(3_000);
  await expect(page).toHaveURL(/\/login/);

  // Should show an error message
  const errorMsg = page.locator('.auth-msg.error');
  await expect(errorMsg).toBeVisible({ timeout: 10_000 });

  // Error message must not be empty
  const errorText = await errorMsg.textContent();
  expect(errorText?.trim().length).toBeGreaterThan(0);

  // Logout button must NOT appear (user is not logged in)
  await expect(page.locator('.logout-button')).not.toBeVisible({ timeout: 2_000 });
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 5: Refresh page while logged in → session survives (httpOnly cookie)
// ─────────────────────────────────────────────────────────────────────────────
test('refresh page while logged in → session survives (silent refresh via httpOnly cookie)', async ({ page }) => {
  // Log in fresh
  await loginUser(page, testEmail, testPassword);

  // Confirm authenticated
  const logoutBtn = page.locator('.logout-button, button:has-text("Logout")');
  await expect(logoutBtn).toBeVisible({ timeout: 15_000 });

  // Hard reload the page
  await page.reload({ waitUntil: 'domcontentloaded' });

  // After reload, AuthContext calls /api/auth/refresh using the httpOnly cookie.
  // This requires: (a) cookie.secure=false in e2e env, (b) withCredentials: true on
  // the original login call. If this fails, it's a session-persistence bug.
  await expect(logoutBtn).toBeVisible({ timeout: 20_000 });
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 6: Access /profile while logged out → blocked (redirect OR inline prompt)
// ─────────────────────────────────────────────────────────────────────────────
test('access /profile while logged out → login required', async ({ page }) => {
  await page.goto('/profile');
  await page.waitForTimeout(2_000); // Allow auth hydration to complete

  const currentUrl = page.url();

  if (currentUrl.includes('/login')) {
    // Redirect-style guard — most strict
    console.log('[auth test 6] /profile → redirected to /login ✓');
  } else {
    // ProfilePage shows inline "Please log in" message — also acceptable
    const bodyText = await page.locator('body').textContent();
    const hasLoginPrompt = /log in|please login|sign in/i.test(bodyText ?? '');
    expect(
      hasLoginPrompt,
      `Expected login prompt or redirect when visiting /profile unauthenticated. Got: "${bodyText?.slice(0, 200)}"`
    ).toBe(true);
    console.log('[auth test 6] /profile → inline login prompt shown ✓');
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 7: Access /simulator while logged out → blocked
// ─────────────────────────────────────────────────────────────────────────────
test('access /simulator while logged out → redirected to /login', async ({ page }) => {
  await page.goto('/simulator');
  await expect(page).toHaveURL(/\/login/, { timeout: 20_000 });
});
