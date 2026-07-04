// e2e/prod-tests/prod-profile.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// PRODUCTION black-box Profile page tests.
// Creates its own e2e_test_ account, fully independent of other specs.
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const {
  uniqueEmail, uniqueUsername, registerUser, loginUser, collectConsoleErrors,
} = require('../prod-helpers/auth');

let testEmail, testUsername, testPassword;

test.beforeAll(() => {
  testEmail    = uniqueEmail('profile');
  testUsername = uniqueUsername('profile');
  testPassword = 'ProdProfile123!';
});

// ── Register the test user ────────────────────────────────────────────────────
test('register profile test user', async ({ page }) => {
  await registerUser(page, {
    email: testEmail,
    username: testUsername,
    password: testPassword,
    context: 'prod-profile-spec:setup',
  });
  await expect(page.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 20_000 });
});

// ── TEST: Profile page shows logged-in user's own data ───────────────────────
test("logged-in user's profile shows their own username and data", async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await loginUser(page, testEmail, testPassword);
  await expect(page.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 20_000 });

  await page.goto('/profile');
  await page.waitForLoadState('domcontentloaded');

  // Must not redirect to /login
  await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 });

  // Username must appear somewhere on the profile
  const bodyText = await page.locator('body').textContent();
  const hasUsername = bodyText?.includes(testUsername);
  expect(hasUsername, `Profile page should display username "${testUsername}"`).toBe(true);

  // Substantial content (not just spinner)
  const hasContent = (bodyText?.length ?? 0) > 200;
  expect(hasContent, 'Profile page should have substantial content').toBe(true);

  console.log('[prod-profile] Username visible on profile page ✓');

  errors.off();
  const critErrors = errors.messages.filter(
    (e) => !e.includes('Warning:') && !e.includes('DevTools')
  );
  if (critErrors.length > 0) {
    console.warn('[prod-profile] Console errors:', critErrors);
  }
  expect(critErrors, `Console errors on /profile: ${critErrors.join('\n')}`).toHaveLength(0);
});

// ── TEST: Profile requires authentication ─────────────────────────────────────
test('profile page is inaccessible when logged out', async ({ page }) => {
  await page.goto('/profile');
  await page.waitForTimeout(3_000); // Allow auth hydration

  const currentUrl = page.url();
  const isRedirected = currentUrl.includes('/login');

  if (isRedirected) {
    console.log('[prod-profile] Unauthenticated /profile → redirected to /login ✓');
  } else {
    const bodyText = await page.locator('body').textContent();
    const hasLoginPrompt = /log in|please login|sign in/i.test(bodyText ?? '');
    expect(
      hasLoginPrompt,
      `Expected login prompt or redirect when visiting /profile unauthenticated. Got: "${bodyText?.slice(0, 200)}"`
    ).toBe(true);
    console.log('[prod-profile] Unauthenticated /profile → inline login prompt shown ✓');
  }
});

// ── TEST: No console errors on profile page ───────────────────────────────────
test('no uncaught JS errors on profile page during normal visit', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await loginUser(page, testEmail, testPassword);
  await page.goto('/profile');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(3_000);

  errors.off();
  const critErrors = errors.messages.filter(
    (e) => !e.includes('Warning:') && !e.includes('DevTools')
  );
  if (critErrors.length > 0) {
    console.error('[prod-profile] Uncaught JS errors:', critErrors);
  }
  expect(critErrors, `Uncaught JS errors on /profile: ${critErrors.join('\n')}`).toHaveLength(0);
});
