// e2e/tests/profile.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// BLACK-BOX Profile page tests.
// Requires: logged-in user. Uses the auth test user created in auth.spec.js
// (but creates its own to be fully independent).
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const { uniqueEmail, registerUser, loginUser, collectConsoleErrors } = require('../helpers/auth');

let testEmail, testUsername, testPassword;

test.beforeAll(() => {
  testEmail    = uniqueEmail('profile');
  testUsername = `profileuser_${Date.now()}`;
  testPassword = 'ProfilePass123!';
});

// ── Register the test user ───────────────────────────────────────────────────
test('register profile test user', async ({ page }) => {
  await registerUser(page, { email: testEmail, username: testUsername, password: testPassword });
  await expect(page.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 10_000 });
});

// ── TEST: Profile page shows the logged-in user's own data ──────────────────
test("logged-in user's profile shows their own username and data", async ({ page }) => {
  const errors = collectConsoleErrors(page);

  // Log in
  await loginUser(page, testEmail, testPassword);
  await expect(page.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 10_000 });

  // Navigate to profile
  await page.goto('/profile');

  // Wait for profile content to load (it fetches from /api/profile/:id)
  await page.waitForLoadState('domcontentloaded');

  // Profile page must render something (not redirect to login)
  await expect(page).not.toHaveURL(/\/login/, { timeout: 10_000 });

  // The username should appear somewhere on the profile page
  const bodyText = await page.locator('body').textContent();
  const hasUsername = bodyText?.includes(testUsername);
  expect(hasUsername, `Profile page should display username "${testUsername}"`).toBe(true);

  // Profile page should have meaningful content (not just a loading spinner)
  const hasContent = (bodyText?.length ?? 0) > 200;
  expect(hasContent, 'Profile page should have substantial content').toBe(true);

  console.log('[profile] Username visible on profile page ✓');

  errors.off();
  const critErrors = errors.messages.filter(
    (e) => !e.includes('Warning:') && !e.includes('DevTools')
  );
  if (critErrors.length > 0) {
    console.warn('[profile] Console errors:', critErrors);
  }
  expect(critErrors, `Console errors on /profile: ${critErrors.join('\n')}`).toHaveLength(0);
});

// ── TEST: Profile page requires authentication ───────────────────────────────
test('profile page is inaccessible when logged out', async ({ page }) => {
  // Start fresh — no login
  await page.goto('/profile');
  await page.waitForTimeout(2_000); // Allow auth hydration

  // ProfilePage shows an inline "Please log in" message rather than redirecting.
  // Both behaviours (redirect OR inline message) are acceptable — check for either.
  const currentUrl = page.url();
  const isRedirected = currentUrl.includes('/login');

  if (isRedirected) {
    console.log('[profile] Unauthenticated /profile → redirected to /login ✓');
  } else {
    // Should show an inline "log in" prompt, not a blank page or crash
    const bodyText = await page.locator('body').textContent();
    const hasLoginPrompt = /log in|please login|sign in/i.test(bodyText ?? '');
    expect(
      hasLoginPrompt,
      `Expected login prompt or redirect when visiting /profile unauthenticated. Got: "${bodyText?.slice(0, 200)}"`
    ).toBe(true);
    console.log('[profile] Unauthenticated /profile → inline login prompt shown ✓');
  }
});

// ── TEST: No console errors on profile page ──────────────────────────────────
test('no uncaught JS errors on profile page during normal visit', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await loginUser(page, testEmail, testPassword);
  await page.goto('/profile');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(2_000);

  errors.off();
  const critErrors = errors.messages.filter(
    (e) => !e.includes('Warning:') && !e.includes('DevTools')
  );
  if (critErrors.length > 0) {
    console.error('[profile] Uncaught JS errors:', critErrors);
  }
  expect(critErrors, `Uncaught JS errors on /profile: ${critErrors.join('\n')}`).toHaveLength(0);
});
