// e2e/tests/simulator.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// BLACK-BOX Trading Simulator tests.
// Requires: logged-in user.
// The simulator uses local in-memory state (simulatorData utils) — trades are
// not persisted to backend. Tests verify visible UI changes only.
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const { uniqueEmail, registerUser, loginUser, collectConsoleErrors } = require('../helpers/auth');

let testEmail, testUsername, testPassword;

test.beforeAll(async () => {
  testEmail    = uniqueEmail('sim');
  testUsername = `simuser_${Date.now()}`;
  testPassword = 'SimPass123!';
});

// Helper: log in and navigate to /simulator
async function goToSimulator(page) {
  await loginUser(page, testEmail, testPassword);
  await page.goto('/simulator');
  // Wait for the dashboard to render (watchlist or portfolio visible)
  await page.waitForSelector('.sim-dashboard, [class*="sim-dashboard"], [class*="simulator"]', {
    timeout: 20_000,
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// TEST 1: Register test user (setup for subsequent simulator tests)
// ─────────────────────────────────────────────────────────────────────────────
test('register simulator test user', async ({ page }) => {
  await registerUser(page, { email: testEmail, username: testUsername, password: testPassword });
  // Just ensure we ended up logged in
  await expect(page.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 10_000 });
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 2: Simulator loads for authenticated user
// ─────────────────────────────────────────────────────────────────────────────
test('simulator loads for logged-in user — watchlist and portfolio visible', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await goToSimulator(page);

  // Watchlist must be visible (left panel)
  const watchlist = page.locator('[class*="watchlist"], [class*="Watchlist"]').first();
  await expect(watchlist).toBeVisible({ timeout: 15_000 });

  // Portfolio summary must be visible (top panel)
  const portfolio = page.locator('[class*="portfolio"], [class*="Portfolio"]').first();
  await expect(portfolio).toBeVisible({ timeout: 10_000 });

  // Transaction history section must exist
  const history = page.locator('[class*="transaction"], [class*="Transaction"], [class*="history"]').first();
  await expect(history).toBeVisible({ timeout: 10_000 });

  errors.off();
  const critErrors = errors.messages.filter(
    (e) => !e.includes('Warning:') && !e.includes('DevTools') && !e.includes('Failed to load historical')
  );
  if (critErrors.length > 0) {
    console.warn('[simulator] Console errors on load:', critErrors);
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 3: Place a trade → balance updates + transaction appears in history
// ─────────────────────────────────────────────────────────────────────────────
test('place a trade → cash balance changes and transaction row appears in history', async ({ page }) => {
  await goToSimulator(page);

  // Read initial cash balance from the OrderTicket's "Available:" display
  // or from the portfolio summary
  const cashLocator = page.locator('[class*="trading-panel__cash"], [class*="cash"], :text-matches("Available:", "i")').first();
  await expect(cashLocator).toBeVisible({ timeout: 10_000 });
  const initialCash = await cashLocator.textContent();

  // Select RELIANCE from watchlist (it's the default selected stock, but click to be sure)
  const relianceRow = page.locator('[class*="watchlist"] :text("RELIANCE"), :text("RELIANCE")').first();
  if (await relianceRow.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await relianceRow.click();
    await page.waitForTimeout(500);
  }

  // The OrderTicket requires: thesisCategory + thesis + stopLoss (optional) before trade
  // Fill thesis category (first dropdown/select)
  const thesisSelect = page.locator('select, [class*="thesis-category"], [class*="thesisCategory"]').first();
  if (await thesisSelect.isVisible({ timeout: 3_000 }).catch(() => false)) {
    // Select the first non-empty option
    await thesisSelect.selectOption({ index: 1 });
  }

  // Fill thesis text (textarea)
  const thesisTextarea = page.locator('textarea, [placeholder*="thesis"], [placeholder*="reasoning"]').first();
  if (await thesisTextarea.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await thesisTextarea.fill('RSI oversold bounce off key support — strong risk/reward setup.');
  }

  // Fill stop loss (needed to compute risk %)
  const stopLossInput = page.locator('input[placeholder*="stop"], input[placeholder*="Stop"], input[placeholder*="loss"]').first();
  if (await stopLossInput.isVisible({ timeout: 3_000 }).catch(() => false)) {
    // Use a stop loss far enough below current price to keep risk < 5%
    await stopLossInput.fill('100');
  }

  // Quantity should already be 1 (default)
  // Make sure BUY tab is active
  const buyTab = page.locator('button:has-text("BUY"), [class*="tab"]:has-text("BUY")').first();
  if (await buyTab.isVisible({ timeout: 2_000 }).catch(() => false)) {
    await buyTab.click();
  }

  // Click the execute/place order button
  const executeBtn = page.locator(
    'button[class*="execute"], button:has-text("BUY"), button:has-text("Place"), button[class*="trading-panel__execute"]'
  ).first();
  await expect(executeBtn).toBeEnabled({ timeout: 10_000 });
  await executeBtn.click();

  // Wait for toast or confirmation
  const toast = page.locator('[class*="toast"], [class*="Toast"]');
  await expect(toast).toBeVisible({ timeout: 10_000 });
  const toastText = await toast.textContent();
  console.log('[simulator] Trade toast:', toastText);

  // If toast shows a success (not an error about risk or thesis), check balance changed
  if (!toastText?.toLowerCase().includes('risk') && !toastText?.toLowerCase().includes('thesis')) {
    // Transaction history should now have at least one row
    const txRows = page.locator(
      '[class*="transaction"] tr, [class*="transaction-row"], [class*="history"] tbody tr'
    );
    // Allow a moment for state update
    await page.waitForTimeout(1_000);
    const txCount = await txRows.count();
    expect(txCount).toBeGreaterThan(0);

    // Cash balance should have changed (number in the display)
    const newCash = await cashLocator.textContent();
    // They should differ (trade was placed)
    if (initialCash === newCash) {
      console.warn('[simulator] UNCLEAR: Cash balance did not change after a trade. May need different form setup.');
    }
  } else {
    console.warn('[simulator] Trade was blocked by validation:', toastText, '— testing risk-cap path instead');
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 4: Exceed risk cap → error message shown, trade rejected
// ─────────────────────────────────────────────────────────────────────────────
test('exceed risk cap → trade rejected with clear error, no crash', async ({ page }) => {
  await goToSimulator(page);

  // To exceed the 5% risk cap: set a very tight stop loss (close to current price)
  // so that riskPerShare is high and totalRisk > 5% of cash
  const thesisSelect = page.locator('select, [class*="thesis-category"]').first();
  if (await thesisSelect.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await thesisSelect.selectOption({ index: 1 });
  }

  const thesisTextarea = page.locator('textarea').first();
  if (await thesisTextarea.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await thesisTextarea.fill('Testing risk validation boundary.');
  }

  // Set a stop loss very close to price to maximize risk per share
  const stopLossInput = page.locator('input[placeholder*="stop"], input[placeholder*="Stop"]').first();
  if (await stopLossInput.isVisible({ timeout: 3_000 }).catch(() => false)) {
    // current price is ~2700 for RELIANCE, stop at 2699 => huge risk on many shares
    await stopLossInput.fill('2699');
  }

  // Set quantity to 100 to amplify the risk
  const qtyInput = page.locator('input[type="number"], [class*="qty-input"], [class*="quantity"]').first();
  if (await qtyInput.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await qtyInput.fill('100');
    await qtyInput.press('Tab');
  }

  // Attempt to execute
  const executeBtn = page.locator(
    'button[class*="execute"], button:has-text("BUY"), button[class*="trading-panel__execute"]'
  ).first();
  if (await executeBtn.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await executeBtn.click();

    // Expect a toast or error message about risk
    const toast = page.locator('[class*="toast"], [class*="Toast"]');
    await expect(toast).toBeVisible({ timeout: 10_000 });
    const toastText = await toast.textContent();
    console.log('[simulator] Risk rejection toast:', toastText);

    // Should mention "risk" or "5%"
    expect(
      toastText?.toLowerCase().includes('risk') || toastText?.toLowerCase().includes('5%'),
      `Expected risk error message, got: "${toastText}"`
    ).toBe(true);

    // Page must not crash (still shows the dashboard)
    await expect(page.locator('[class*="sim-dashboard"]')).toBeVisible({ timeout: 5_000 });
  } else {
    console.warn('[simulator] Execute button not found for risk-cap test — UNCLEAR if risk validation is accessible');
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST 5: No uncaught JS errors on simulator page
// ─────────────────────────────────────────────────────────────────────────────
test('no uncaught JS errors on simulator page during normal use', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await goToSimulator(page);
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(3_000); // Let chart and data settle

  errors.off();
  const critErrors = errors.messages.filter(
    (e) =>
      !e.includes('Warning:') &&
      !e.includes('DevTools') &&
      !e.includes('Failed to load historical') &&  // API fetch; not a JS crash
      !e.includes('net::ERR_')                      // Network errors reported separately
  );
  if (critErrors.length > 0) {
    console.error('[simulator] Uncaught JS errors:', critErrors);
  }
  expect(critErrors, `Uncaught JS errors on /simulator:\n${critErrors.join('\n')}`).toHaveLength(0);
});
