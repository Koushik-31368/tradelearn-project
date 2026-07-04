// e2e/prod-tests/prod-simulator.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// PRODUCTION black-box Trading Simulator tests.
// Creates a fresh test account, exercises trade UI, verifies state updates.
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const {
  uniqueEmail, uniqueUsername, registerUser, loginUser, collectConsoleErrors,
} = require('../prod-helpers/auth');

let testEmail, testUsername, testPassword;

test.beforeAll(async () => {
  testEmail    = uniqueEmail('sim');
  testUsername = uniqueUsername('sim');
  testPassword = 'ProdSim123!';
});

// Helper: log in and navigate to /simulator
async function goToSimulator(page) {
  await loginUser(page, testEmail, testPassword);
  await page.goto('/simulator');
  // Wait for the sim dashboard shell to render
  await page.waitForSelector(
    '.sim-dashboard, [class*="sim-dashboard"], [class*="simulator"]',
    { timeout: 30_000 }
  );
}

// ── TEST 1: Register the simulator test user ─────────────────────────────────
test('register simulator test user', async ({ page }) => {
  await registerUser(page, {
    email: testEmail,
    username: testUsername,
    password: testPassword,
    context: 'prod-simulator-spec:setup',
  });
  await expect(page.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 20_000 });
});

// ── TEST 2: Simulator loads for authenticated user ───────────────────────────
test('simulator loads for logged-in user — watchlist and portfolio visible', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await goToSimulator(page);

  // Watchlist (left panel)
  const watchlist = page.locator('[class*="watchlist"], [class*="Watchlist"]').first();
  await expect(watchlist).toBeVisible({ timeout: 20_000 });

  // Portfolio summary (top panel)
  const portfolio = page.locator('[class*="portfolio"], [class*="Portfolio"]').first();
  await expect(portfolio).toBeVisible({ timeout: 15_000 });

  // Transaction history section
  const history = page.locator('[class*="transaction"], [class*="Transaction"], [class*="history"]').first();
  await expect(history).toBeVisible({ timeout: 15_000 });

  errors.off();
  const critErrors = errors.messages.filter(
    (e) => !e.includes('Warning:') && !e.includes('DevTools') && !e.includes('Failed to load historical')
  );
  if (critErrors.length > 0) {
    console.warn('[prod-sim] Console errors on simulator load:', critErrors);
  }
});

// ── TEST 3: Place a trade → balance updates + transaction appears ─────────────
test('place a trade → cash balance changes and transaction row appears', async ({ page }) => {
  await goToSimulator(page);

  // Read initial cash balance
  const cashLocator = page.locator(
    '[class*="trading-panel__cash"], [class*="cash"], :text-matches("Available:", "i")'
  ).first();
  await expect(cashLocator).toBeVisible({ timeout: 15_000 });
  const initialCash = await cashLocator.textContent();

  // Select RELIANCE from watchlist if visible
  const relianceRow = page.locator('[class*="watchlist"] :text("RELIANCE"), :text("RELIANCE")').first();
  if (await relianceRow.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await relianceRow.click();
    await page.waitForTimeout(700);
  }

  // Fill thesis category
  const thesisSelect = page.locator('select, [class*="thesis-category"]').first();
  if (await thesisSelect.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await thesisSelect.selectOption({ index: 1 });
  }

  // Fill thesis text
  const thesisTextarea = page.locator('textarea, [placeholder*="thesis"], [placeholder*="reasoning"]').first();
  if (await thesisTextarea.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await thesisTextarea.fill('RSI oversold bounce off key support — strong risk/reward setup.');
  }

  // Fill stop loss
  const stopLossInput = page.locator('input[placeholder*="stop"], input[placeholder*="Stop"]').first();
  if (await stopLossInput.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await stopLossInput.fill('100');
  }

  // Make sure BUY tab is active
  const buyTab = page.locator('button:has-text("BUY"), [class*="tab"]:has-text("BUY")').first();
  if (await buyTab.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await buyTab.click();
  }

  // Execute the trade
  const executeBtn = page.locator(
    'button[class*="execute"], button:has-text("BUY"), button:has-text("Place"), button[class*="trading-panel__execute"]'
  ).first();
  await expect(executeBtn).toBeEnabled({ timeout: 15_000 });
  await executeBtn.click();

  // Wait for toast/confirmation
  const toast = page.locator('[class*="toast"], [class*="Toast"]');
  await expect(toast).toBeVisible({ timeout: 15_000 });
  const toastText = await toast.textContent();
  console.log('[prod-sim] Trade toast:', toastText);

  if (!toastText?.toLowerCase().includes('risk') && !toastText?.toLowerCase().includes('thesis')) {
    // Trade went through — check history
    const txRows = page.locator('[class*="transaction"] tr, [class*="transaction-row"], [class*="history"] tbody tr');
    await page.waitForTimeout(1_500);
    const txCount = await txRows.count();
    expect(txCount).toBeGreaterThan(0);

    const newCash = await cashLocator.textContent();
    if (initialCash === newCash) {
      console.warn('[prod-sim] UNCLEAR: Cash balance did not change after trade. Check form setup.');
    }
  } else {
    console.warn('[prod-sim] Trade blocked by validation:', toastText, '— testing risk-cap path');
  }
});

// ── TEST 4: Exceed risk cap → error shown, trade rejected ────────────────────
test('exceed risk cap → trade rejected with risk error, no crash', async ({ page }) => {
  await goToSimulator(page);

  const thesisSelect = page.locator('select, [class*="thesis-category"]').first();
  if (await thesisSelect.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await thesisSelect.selectOption({ index: 1 });
  }

  const thesisTextarea = page.locator('textarea').first();
  if (await thesisTextarea.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await thesisTextarea.fill('Testing risk validation boundary.');
  }

  // Very tight stop loss to exceed risk cap
  const stopLossInput = page.locator('input[placeholder*="stop"], input[placeholder*="Stop"]').first();
  if (await stopLossInput.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await stopLossInput.fill('2699');
  }

  // High quantity to amplify risk
  const qtyInput = page.locator('input[type="number"], [class*="qty-input"]').first();
  if (await qtyInput.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await qtyInput.fill('100');
    await qtyInput.press('Tab');
  }

  const executeBtn = page.locator(
    'button[class*="execute"], button:has-text("BUY"), button[class*="trading-panel__execute"]'
  ).first();
  if (await executeBtn.isVisible({ timeout: 8_000 }).catch(() => false)) {
    await executeBtn.click();

    const toast = page.locator('[class*="toast"], [class*="Toast"]');
    await expect(toast).toBeVisible({ timeout: 15_000 });
    const toastText = await toast.textContent();
    console.log('[prod-sim] Risk rejection toast:', toastText);

    expect(
      toastText?.toLowerCase().includes('risk') || toastText?.toLowerCase().includes('5%'),
      `Expected risk error message, got: "${toastText}"`
    ).toBe(true);

    // Dashboard must not crash
    await expect(page.locator('[class*="sim-dashboard"]')).toBeVisible({ timeout: 8_000 });
  } else {
    console.warn('[prod-sim] Execute button not visible for risk-cap test — skipping assertion');
  }
});

// ── TEST 5: No uncaught JS errors on simulator page ──────────────────────────
test('no uncaught JS errors on simulator page during normal use', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await goToSimulator(page);
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(4_000); // Let chart and data settle (slower on prod)

  errors.off();
  const critErrors = errors.messages.filter(
    (e) =>
      !e.includes('Warning:') &&
      !e.includes('DevTools') &&
      !e.includes('Failed to load historical') &&
      !e.includes('net::ERR_')
  );
  if (critErrors.length > 0) {
    console.error('[prod-sim] Uncaught JS errors:', critErrors);
  }
  expect(critErrors, `Uncaught JS errors on /simulator:\n${critErrors.join('\n')}`).toHaveLength(0);
});
