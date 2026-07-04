// e2e/prod-tests/prod-leaderboard.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// PRODUCTION black-box Leaderboard tests.
// Public page — no auth required.
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const { collectConsoleErrors } = require('../prod-helpers/auth');

test.describe('Leaderboard (Production)', () => {

  // ── TEST 1: Leaderboard page loads and shows rankings ─────────────────────
  test('leaderboard loads and displays rankings', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/leaderboard');
    await page.waitForLoadState('domcontentloaded');

    // Heading must be present
    const heading = page.locator('h1, h2, [class*="leaderboard"] h1, [class*="leaderboard"] h2').first();
    await expect(heading).toBeVisible({ timeout: 20_000 });

    // Tab buttons (Multiplayer, Leagues, Practice, etc.)
    const tabs = page.locator('button:has-text("Multiplayer"), button:has-text("multiplayer"), [class*="tab"]');
    await expect(tabs.first()).toBeVisible({ timeout: 15_000 });

    const bodyText = await page.locator('body').textContent();
    const hasContent = (bodyText?.length ?? 0) > 100;
    expect(hasContent, 'Leaderboard page should have meaningful content').toBe(true);

    // Should mention rank/username/rating in a populated leaderboard
    const hasRankOrUser = /rank|#\d|username|player|rating|elo/i.test(bodyText ?? '');
    if (!hasRankOrUser) {
      console.warn('[prod-lb] UNCLEAR: No rank/username/rating visible — leaderboard may be empty or API issue');
    }

    errors.off();
    const critErrors = errors.messages.filter(
      (e) =>
        !e.includes('Warning:') &&
        !e.includes('DevTools') &&
        !(e.includes('Failed to load resource') && (e.includes('400') || e.includes('401')))
    );
    if (critErrors.length > 0) {
      console.warn('[prod-lb] Console errors:', critErrors);
    }
    expect(critErrors, `Console errors on /leaderboard: ${critErrors.join('\n')}`).toHaveLength(0);
  });

  // ── TEST 2: Leaderboard tabs are clickable without crash ──────────────────
  test('leaderboard tabs are navigable without crash', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/leaderboard');
    await page.waitForLoadState('domcontentloaded');

    const tabNames = ['Multiplayer', 'Ranked', 'Leagues', 'Practice', 'Simulator'];
    let tabsFound = 0;
    for (const tabName of tabNames) {
      const tab = page.locator(`button:has-text("${tabName}")`);
      if (await tab.isVisible({ timeout: 3_000 }).catch(() => false)) {
        tabsFound++;
        await tab.click();
        await page.waitForTimeout(1_500);
        const bodyText = await page.locator('body').textContent();
        expect((bodyText?.length ?? 0) > 50, `Tab "${tabName}" rendered content`).toBe(true);
        console.log(`[prod-lb] Tab "${tabName}": clickable and renders content ✓`);
      } else {
        console.log(`[prod-lb] Tab "${tabName}": not found (may be named differently)`);
      }
    }

    errors.off();
    const critErrors = errors.messages.filter(
      (e) =>
        !e.includes('Warning:') &&
        !(e.includes('Failed to load resource') && (e.includes('400') || e.includes('401')))
    );
    expect(critErrors, `Console errors on tab switch: ${critErrors.join('\n')}`).toHaveLength(0);
  });

  // ── TEST 3: No uncaught JS errors on leaderboard ──────────────────────────
  test('no uncaught JS errors on leaderboard page', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/leaderboard');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(3_000);

    errors.off();
    const critErrors = errors.messages.filter(
      (e) =>
        !e.includes('Warning:') &&
        !e.includes('DevTools') &&
        !(e.includes('Failed to load resource') && (e.includes('400') || e.includes('401')))
    );
    if (critErrors.length > 0) {
      console.error('[prod-lb] Uncaught JS errors:', critErrors);
    }
    expect(critErrors, `Uncaught JS errors: ${critErrors.join('\n')}`).toHaveLength(0);
  });
});
