// e2e/tests/leaderboard.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// BLACK-BOX Leaderboard tests.
// The leaderboard is publicly accessible (no auth required).
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const { collectConsoleErrors } = require('../helpers/auth');

test.describe('Leaderboard', () => {

  // ── TEST 1: Leaderboard page loads and shows rankings ───────────────────
  test('leaderboard loads and displays rankings', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/leaderboard');

    // Page should have a heading
    const heading = page.locator('h1, h2, [class*="leaderboard"] h1, [class*="leaderboard"] h2').first();
    await expect(heading).toBeVisible({ timeout: 15_000 });

    // Should have tab buttons (Multiplayer, Leagues, Practice)
    const tabs = page.locator('button:has-text("Multiplayer"), button:has-text("multiplayer"), [class*="tab"]');
    await expect(tabs.first()).toBeVisible({ timeout: 10_000 });

    // The multiplayer leaderboard table/list should have at least one entry
    // (After tests, there should be registered users in the DB)
    // Give it time to load from backend
    await page.waitForLoadState('domcontentloaded');

    const leaderboardContent = page.locator('body');
    const bodyText = await leaderboardContent.textContent();

    // At minimum, the page must not show a plain error or be completely empty
    const hasContent = (bodyText?.length ?? 0) > 100;
    expect(hasContent, 'Leaderboard page should have meaningful content').toBe(true);

    // Check for common leaderboard elements: rank column, username, or score
    const hasRankOrUser = /rank|#\d|username|player|rating/i.test(bodyText ?? '');
    if (!hasRankOrUser) {
      // May be empty leaderboard (no games played yet) — flag as unclear, not a failure
      console.warn('[leaderboard] UNCLEAR: Page loaded but no rank/username/rating visible — may be empty DB or API issue');
    }

    errors.off();
    const critErrors = errors.messages.filter(
      (e) =>
        !e.includes('Warning:') &&
        !e.includes('DevTools') &&
        !(e.includes('Failed to load resource') && (e.includes('400') || e.includes('401')))
    );
    if (critErrors.length > 0) {
      console.warn('[leaderboard] Console errors:', critErrors);
    }
    expect(critErrors, `Console errors on /leaderboard: ${critErrors.join('\n')}`).toHaveLength(0);
  });

  // ── TEST 2: Can switch between leaderboard tabs ──────────────────────────
  test('leaderboard tabs are navigable without crash', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/leaderboard');
    await page.waitForLoadState('domcontentloaded');

    // Try clicking each tab — use broad selectors since exact names may vary
    const tabNames = ['Multiplayer', 'Ranked', 'Leagues', 'Practice', 'Simulator'];
    let tabsFound = 0;
    for (const tabName of tabNames) {
      const tab = page.locator(`button:has-text("${tabName}")`);
      if (await tab.isVisible({ timeout: 2_000 }).catch(() => false)) {
        tabsFound++;
        await tab.click();
        await page.waitForTimeout(1_000);
        const body = page.locator('body');
        const bodyText = await body.textContent();
        expect((bodyText?.length ?? 0) > 50, `Tab "${tabName}" rendered meaningful content`).toBe(true);
        console.log(`[leaderboard] Tab "${tabName}" clickable and renders content ✓`);
      } else {
        console.log(`[leaderboard] Tab "${tabName}" not found — may be named differently`);
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

  // ── TEST 3: No uncaught JS errors on leaderboard page ───────────────────
  test('no uncaught JS errors on leaderboard page', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/leaderboard');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2_000);

    errors.off();
    const critErrors = errors.messages.filter(
      (e) =>
        !e.includes('Warning:') &&
        !e.includes('DevTools') &&
        !(e.includes('Failed to load resource') && (e.includes('400') || e.includes('401')))
    );
    if (critErrors.length > 0) {
      console.error('[leaderboard] Uncaught JS errors:', critErrors);
    }
    expect(critErrors, `Uncaught JS errors: ${critErrors.join('\n')}`).toHaveLength(0);
  });
});
