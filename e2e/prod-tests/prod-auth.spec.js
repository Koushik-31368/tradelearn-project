// e2e/prod-tests/prod-auth.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// PRODUCTION black-box auth flow tests.
// Runs against live Vercel + Render deployment.
// All test accounts prefixed "e2e_test_" and logged to prod-test-accounts.log.
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const {
  uniqueEmail, uniqueUsername, registerUser, loginUser,
  isLoggedIn, collectConsoleErrors,
} = require('../prod-helpers/auth');

let testEmail, testUsername, testPassword;

test.beforeAll(() => {
  testEmail    = uniqueEmail('auth');
  testUsername = uniqueUsername('auth');
  testPassword = 'ProdTest123!';
});

// ── TEST 1: Register a new account ──────────────────────────────────────────
test('register a new account → lands in logged-in state', async ({ page }) => {
  await registerUser(page, {
    email: testEmail,
    username: testUsername,
    password: testPassword,
    context: 'prod-auth-spec:register-test',
  });

  // Should have left /register
  await expect(page).not.toHaveURL(/\/register/, { timeout: 5_000 });

  // Navbar should show Logout button (logged-in state)
  const logoutBtn = page.locator('.logout-button, button:has-text("Logout")');
  await expect(logoutBtn).toBeVisible({ timeout: 20_000 });
});

// ── TEST 2: Log out → protected pages redirect to /login ────────────────────
test('log out → /simulator redirects to /login', async ({ page }) => {
  await loginUser(page, testEmail, testPassword);

  const logoutBtn = page.locator('.logout-button, button:has-text("Logout")');
  await expect(logoutBtn).toBeVisible({ timeout: 20_000 });

  await logoutBtn.click();
  await page.waitForTimeout(2_000); // Allow logout API call + state clear

  await page.goto('/simulator');
  await expect(page).toHaveURL(/\/login/, { timeout: 25_000 });
});

// ── TEST 3: Log in with correct credentials → access restored ───────────────
test('log in with correct credentials → access restored', async ({ page }) => {
  await loginUser(page, testEmail, testPassword);
  await expect(page).not.toHaveURL(/\/login/, { timeout: 8_000 });

  const logoutBtn = page.locator('.logout-button, button:has-text("Logout")');
  await expect(logoutBtn).toBeVisible({ timeout: 20_000 });
});

// ── TEST 4: Wrong password → clear error, no crash ──────────────────────────
test('log in with wrong password → clear error shown, no silent failure', async ({ page }) => {
  await page.goto('/login');

  await page.fill('input[type="email"]', testEmail);
  await page.fill('input[type="password"]', 'WRONG_PROD_PASSWORD_xyz999');
  await page.click('button[type="submit"]');

  // Allow API response
  await page.waitForTimeout(4_000);
  await expect(page).toHaveURL(/\/login/);

  // Error message must appear
  const errorMsg = page.locator('.auth-msg.error');
  await expect(errorMsg).toBeVisible({ timeout: 15_000 });
  const errorText = await errorMsg.textContent();
  expect(errorText?.trim().length).toBeGreaterThan(0);

  // Logout button must NOT appear
  await expect(page.locator('.logout-button')).not.toBeVisible({ timeout: 3_000 });
});

// ── TEST 5: Session survives page refresh (httpOnly cookie + silent refresh) ─
test('session survives page reload — httpOnly cookie silent refresh', async ({ page }) => {
  await loginUser(page, testEmail, testPassword);

  const logoutBtn = page.locator('.logout-button, button:has-text("Logout")');
  await expect(logoutBtn).toBeVisible({ timeout: 20_000 });

  // Hard reload
  await page.reload({ waitUntil: 'domcontentloaded' });

  // AuthContext should call /api/auth/refresh via httpOnly cookie.
  // This is the session-persistence test — failure here = real session bug.
  await expect(logoutBtn).toBeVisible({ timeout: 25_000 });
});

// ── TEST 6: /profile while logged out → login prompt or redirect ────────────
test('access /profile while logged out → login required', async ({ page }) => {
  await page.goto('/profile');
  await page.waitForTimeout(3_000); // Allow auth hydration

  const currentUrl = page.url();
  if (currentUrl.includes('/login')) {
    console.log('[prod-auth] /profile → redirected to /login ✓');
  } else {
    const bodyText = await page.locator('body').textContent();
    const hasLoginPrompt = /log in|please login|sign in/i.test(bodyText ?? '');
    expect(
      hasLoginPrompt,
      `Expected login prompt or redirect when visiting /profile unauthenticated. Got: "${bodyText?.slice(0, 200)}"`
    ).toBe(true);
    console.log('[prod-auth] /profile → inline login prompt shown ✓');
  }
});

// ── TEST 7: /simulator while logged out → redirected ────────────────────────
test('access /simulator while logged out → redirected to /login', async ({ page }) => {
  await page.goto('/simulator');
  await expect(page).toHaveURL(/\/login/, { timeout: 25_000 });
});
