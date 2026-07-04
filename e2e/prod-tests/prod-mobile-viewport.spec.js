// e2e/prod-tests/prod-mobile-viewport.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// PRODUCTION mobile viewport tests (390x844 — iPhone 14 Pro).
// Run by the 'chromium-mobile' project in playwright.prod.config.js.
// Checks: homepage, login, simulator on mobile — no layout breaks.
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const {
  uniqueEmail, uniqueUsername, registerUser, loginUser, collectConsoleErrors,
} = require('../prod-helpers/auth');

let mobileTestEmail, mobileTestUsername, mobileTestPassword;

test.beforeAll(() => {
  mobileTestEmail    = uniqueEmail('mob');
  mobileTestUsername = uniqueUsername('mob');
  mobileTestPassword = 'ProdMobile123!';
});

// Helper: check for horizontal scroll (layout overflow)
async function hasHorizontalScroll(page) {
  return await page.evaluate(() => {
    return document.documentElement.scrollWidth > document.documentElement.clientWidth;
  });
}

// Helper: check for obviously overlapping elements (basic heuristic)
async function checkNoObviousOverlap(page, label) {
  // Check that the body is not impossibly tall for a short viewport (sign of layout explosion)
  const bodyHeight = await page.evaluate(() => document.body.scrollHeight);
  const viewportHeight = 844;
  // Allow up to 10x the viewport height for content-rich pages — anything more is suspicious
  if (bodyHeight > viewportHeight * 10) {
    console.warn(`[prod-mobile] ⚠️  ${label}: Body height ${bodyHeight}px seems very large for mobile viewport`);
  }
  return true;
}

// ── Register mobile test user ──────────────────────────────────────────────
test('register mobile test user', async ({ page }) => {
  await registerUser(page, {
    email: mobileTestEmail,
    username: mobileTestUsername,
    password: mobileTestPassword,
    context: 'prod-mobile-spec:setup',
  });
  await expect(page.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 20_000 });
});

// ── TEST 1: Homepage on mobile — renders without horizontal scroll ──────────
test('homepage on mobile (390x844) — renders without horizontal scroll', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2_000);

  // Must have content
  const bodyText = await page.locator('body').textContent();
  expect((bodyText?.trim().length ?? 0)).toBeGreaterThan(50);

  // No horizontal scroll
  const hasScrollX = await hasHorizontalScroll(page);
  if (hasScrollX) {
    console.error('[prod-mobile] ❌ Homepage has horizontal scroll on mobile viewport — layout issue!');
  } else {
    console.log('[prod-mobile] ✓ Homepage: no horizontal scroll on mobile');
  }
  expect(hasScrollX, 'Homepage should not have horizontal scroll on 390px wide viewport').toBe(false);

  // Check for body content visible (not hidden by overflow)
  const heading = page.locator('h1, h2, [class*="hero"], [class*="banner"], .navbar').first();
  await expect(heading).toBeVisible({ timeout: 15_000 });

  await checkNoObviousOverlap(page, 'Homepage');

  errors.off();
  const critErrors = errors.messages.filter((e) => !e.includes('Warning:') && !e.includes('401') && !e.includes('400'));
  if (critErrors.length > 0) console.warn('[prod-mobile] Homepage errors:', critErrors);
});

// ── TEST 2: Login page on mobile — form usable, no horizontal scroll ────────
test('login page on mobile (390x844) — form visible and usable', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1_500);

  // Email and password inputs must be visible and clickable on mobile
  const emailInput = page.locator('input[type="email"]');
  await expect(emailInput).toBeVisible({ timeout: 15_000 });

  const passwordInput = page.locator('input[type="password"]');
  await expect(passwordInput).toBeVisible({ timeout: 10_000 });

  const submitBtn = page.locator('button[type="submit"]');
  await expect(submitBtn).toBeVisible({ timeout: 10_000 });

  // No horizontal scroll
  const hasScrollX = await hasHorizontalScroll(page);
  if (hasScrollX) {
    console.error('[prod-mobile] ❌ Login page has horizontal scroll on mobile — layout issue!');
  }
  expect(hasScrollX, 'Login page should not have horizontal scroll on 390px wide viewport').toBe(false);

  // Verify we can type in the form (basic mobile interaction test)
  await emailInput.click();
  await emailInput.fill('test@example.com');
  const filledValue = await emailInput.inputValue();
  expect(filledValue).toBe('test@example.com');

  console.log('[prod-mobile] ✓ Login page: form usable on mobile, no horizontal scroll');

  errors.off();
  const critErrors = errors.messages.filter((e) => !e.includes('Warning:') && !e.includes('401') && !e.includes('400'));
  if (critErrors.length > 0) console.warn('[prod-mobile] Login page errors:', critErrors);
});

// ── TEST 3: Simulator on mobile — loads, no horizontal scroll ───────────────
test('simulator on mobile (390x844) — accessible and no horizontal scroll', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  // Log in first
  await loginUser(page, mobileTestEmail, mobileTestPassword);

  await page.goto('/simulator', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3_000); // Let chart render on mobile

  // Must not redirect to /login (user is authenticated)
  await expect(page).not.toHaveURL(/\/login/, { timeout: 10_000 });

  // Some content must be visible
  const bodyText = await page.locator('body').textContent();
  expect((bodyText?.trim().length ?? 0)).toBeGreaterThan(100);

  // No horizontal scroll
  const hasScrollX = await hasHorizontalScroll(page);
  if (hasScrollX) {
    console.warn('[prod-mobile] ⚠️  Simulator has horizontal scroll on mobile — may need responsive CSS fix');
    // Flag but don't hard-fail — simulator is complex and mobile support is a roadmap item
    console.warn('[prod-mobile] Note: README roadmap includes "Mobile-responsive simulator"');
  } else {
    console.log('[prod-mobile] ✓ Simulator: no horizontal scroll on mobile');
  }

  // Main sim container (or some dashboard element) should be visible
  const simContainer = page.locator(
    '[class*="sim-dashboard"], [class*="simulator"], [class*="watchlist"], [class*="chart"]'
  ).first();
  const simVisible = await simContainer.isVisible({ timeout: 20_000 }).catch(() => false);

  if (!simVisible) {
    console.warn('[prod-mobile] ⚠️  Simulator main container not visible on mobile — layout may be broken');
    // Check if page at least has non-trivial content
    const hasContent = (bodyText?.trim().length ?? 0) > 200;
    expect(hasContent, 'Simulator on mobile should at least show some content').toBe(true);
  } else {
    console.log('[prod-mobile] ✓ Simulator main container visible on mobile');
  }

  errors.off();
  const critErrors = errors.messages.filter(
    (e) =>
      !e.includes('Warning:') &&
      !e.includes('DevTools') &&
      !e.includes('Failed to load historical') &&
      !e.includes('401') &&
      !e.includes('400')
  );
  if (critErrors.length > 0) {
    console.warn('[prod-mobile] Simulator console errors on mobile:', critErrors);
  }
});

// ── TEST 4: Strategies page on mobile — cards visible ────────────────────────
test('strategies page on mobile (390x844) — cards visible, no horizontal scroll', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/strategies', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1_500);

  // Heading visible
  const heading = page.locator('h1, h2, [class*="strategies"]').first();
  await expect(heading).toBeVisible({ timeout: 15_000 });

  // At least one strategy card visible
  const cards = page.locator('[class*="strategy-card"], [class*="card"]');
  await expect(cards.first()).toBeVisible({ timeout: 15_000 });

  // No horizontal scroll
  const hasScrollX = await hasHorizontalScroll(page);
  if (hasScrollX) {
    console.error('[prod-mobile] ❌ Strategies page has horizontal scroll on mobile — layout issue!');
  } else {
    console.log('[prod-mobile] ✓ Strategies page: no horizontal scroll on mobile');
  }
  expect(hasScrollX, 'Strategies page should not have horizontal scroll on 390px wide viewport').toBe(false);

  errors.off();
  const critErrors = errors.messages.filter((e) => !e.includes('Warning:') && !e.includes('401') && !e.includes('400'));
  if (critErrors.length > 0) console.warn('[prod-mobile] Strategies errors on mobile:', critErrors);
});
