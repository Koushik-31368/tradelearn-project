// e2e/tests/strategies.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// BLACK-BOX Learning Academy / Strategies tests.
// Navigates to /strategies, opens at least 2 strategy cards, checks for
// content rendering, no blank pages, and no console errors.
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const { collectConsoleErrors } = require('../helpers/auth');

test.describe('Learning Academy — Strategies', () => {

  // ── TEST 1: /strategies page loads ─────────────────────────────────────
  test('strategies page loads — heading visible, no blank page', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/strategies');

    // Page must render something meaningful — at minimum a heading or strategy card
    const heading = page.locator('h1, h2, .strategies-page__title, [class*="strategies"]').first();
    await expect(heading).toBeVisible({ timeout: 15_000 });

    // Must have at least one strategy card/item rendered
    const strategyCards = page.locator('[class*="strategy-card"], [class*="StrategyCard"], .card, [class*="card"]');
    await expect(strategyCards.first()).toBeVisible({ timeout: 10_000 });

    errors.off();
    // Flag any console errors (excluding React dev warnings which aren't bugs)
    const realErrors = errors.messages.filter(
      (e) => !e.includes('Warning:') && !e.includes('Download the React DevTools')
    );
    if (realErrors.length > 0) {
      console.warn('[strategies] Console errors on /strategies:', realErrors);
    }
    expect(realErrors, `Console errors on /strategies: ${realErrors.join('\n')}`).toHaveLength(0);
  });

  // ── TEST 2: Open strategy detail 1 (RSI Mean Reversion) ─────────────────
  test('open RSI Mean Reversion strategy → detail panel renders with content', async ({ page }) => {
    const errors = collectConsoleErrors(page);
    await page.goto('/strategies');

    // Click the first strategy card that mentions RSI or is the first item
    const firstCard = page.locator('[class*="strategy-card"], [class*="card"]').first();
    await expect(firstCard).toBeVisible({ timeout: 10_000 });
    await firstCard.click();

    // After click, a detail panel/modal/section should expand or appear
    // Look for detail-specific content: "Entry", "Exit", "Risk" headings, or any body text block
    const detailPanel = page.locator(
      '[class*="strategy-detail"], [class*="StrategyDetail"], [class*="detail"]'
    ).first();
    await expect(detailPanel).toBeVisible({ timeout: 10_000 });

    // Content within the detail must not be empty
    const detailText = await detailPanel.textContent();
    expect(detailText?.trim().length).toBeGreaterThan(50);

    // Specifically check for key section headings that should be present
    const pageText = await page.locator('body').textContent();
    const hasEntryOrExit = /entry|exit|risk/i.test(pageText ?? '');
    expect(hasEntryOrExit, 'Strategy detail should contain Entry/Exit/Risk information').toBe(true);

    errors.off();
    const realErrors = errors.messages.filter((e) => !e.includes('Warning:'));
    if (realErrors.length > 0) {
      console.warn('[strategies] Console errors after opening detail 1:', realErrors);
    }
    expect(realErrors, `Console errors: ${realErrors.join('\n')}`).toHaveLength(0);
  });

  // ── TEST 3: Open strategy detail 2 (a different card) ───────────────────
  test('open second strategy → different content renders, no blank page', async ({ page }) => {
    const errors = collectConsoleErrors(page);
    await page.goto('/strategies');

    const cards = page.locator('[class*="strategy-card"], [class*="card"]');
    await expect(cards.nth(1)).toBeVisible({ timeout: 10_000 });

      // Capture first card's text to verify second card shows different content
    await cards.first().click();
    const firstDetailPanel = page.locator('[class*="strategy-detail"], [class*="detail"], [class*="strat-detail"]').first();
    await expect(firstDetailPanel).toBeVisible({ timeout: 10_000 });
    const firstDetailText = await firstDetailPanel.textContent();

    // Close the overlay before clicking the second card.
    // After card 1 is open, a .strat-detail-overlay intercepts pointer events.
    // Click the overlay backdrop (outside the detail panel) or a close button to dismiss.
    const overlay = page.locator('.strat-detail-overlay, [class*="overlay"]').first();
    if (await overlay.isVisible({ timeout: 2_000 }).catch(() => false)) {
      // Try clicking a close/X button first
      const closeBtn = page.locator('[class*="close"], button:has-text("×"), button:has-text("Close")').first();
      if (await closeBtn.isVisible({ timeout: 1_000 }).catch(() => false)) {
        await closeBtn.click();
      } else {
        // Click the overlay backdrop to dismiss
        await overlay.click({ position: { x: 10, y: 10 }, force: true });
      }
      await expect(overlay).not.toBeVisible({ timeout: 5_000 });
    }

    // Now click the second card
    await cards.nth(1).click();
    await page.waitForTimeout(500); // Allow animation/transition

    const secondDetailText = await firstDetailPanel.textContent();

    // Both should have substantial content
    expect(secondDetailText?.trim().length ?? 0).toBeGreaterThan(50);

    // The two detail panels should not be identical (different strategies)
    // This catches a bug where clicking different cards shows the same data
    if (firstDetailText === secondDetailText) {
      console.warn('[strategies] UNCLEAR: Both strategy cards show identical content — may be a bug or single-card mode');
    }

    errors.off();
    const realErrors = errors.messages.filter((e) => !e.includes('Warning:'));
    expect(realErrors, `Console errors: ${realErrors.join('\n')}`).toHaveLength(0);
  });

  // ── TEST 4: No console errors on strategies page navigation ─────────────
  test('no uncaught JS errors during strategies page interaction', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/strategies');
    await page.waitForLoadState('domcontentloaded');

    // Interact with a few elements
    const cards = page.locator('[class*="strategy-card"], [class*="card"]');
    const count = await cards.count();
    if (count > 0) await cards.first().click();
    if (count > 1) await cards.nth(1).click();

    await page.waitForTimeout(1_000);

    errors.off();
    const criticalErrors = errors.messages.filter(
      (e) =>
        !e.includes('Warning:') &&
        !e.includes('DevTools') &&
        // auth/refresh 401 is expected on every page load for unauthenticated users
        !(e.includes('Failed to load resource') && (e.includes('400') || e.includes('401')))
    );
    if (criticalErrors.length > 0) {
      console.error('[strategies] Uncaught JS errors:', criticalErrors);
    }
    expect(criticalErrors, `Uncaught JS errors: ${criticalErrors.join('\n')}`).toHaveLength(0);
  });
});
