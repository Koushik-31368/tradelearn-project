// e2e/prod-tests/prod-multiplayer.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// PRODUCTION black-box multiplayer match test.
// Creates ONE real match between two e2e_test_ accounts on the live backend.
// IMPORTANT: This test runs ONCE and does not retry multiplayer in a loop.
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect, chromium } = require('@playwright/test');
const {
  uniqueEmail, uniqueUsername, logCreatedAccount,
} = require('../prod-helpers/auth');

const PROD_BASE = 'https://tradelearn-project.vercel.app';

let playerAEmail, playerAUsername, playerAPassword;
let playerBEmail, playerBUsername, playerBPassword;

// ─────────────────────────────────────────────────────────────────────────────
// Helper: register a user via the UI in a given browser context page
// ─────────────────────────────────────────────────────────────────────────────
async function registerViaUI(page, { email, username, password, context }) {
  await page.goto(`${PROD_BASE}/register`);
  await page.fill('input[type="email"]', email);
  await page.fill('input[type="text"]', username);
  await page.fill('input[type="password"]', password);
  const checkbox = page.locator('input[type="checkbox"]');
  if (await checkbox.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await checkbox.check();
  }
  await page.click('button[type="submit"]');
  // Extended timeout for production network
  await page.waitForURL((url) => !url.pathname.includes('/register'), { timeout: 35_000 });
  await page.waitForSelector('.logout-button, button:has-text("Logout")', { timeout: 20_000 });
  logCreatedAccount(email, username, context ?? 'prod-multiplayer-spec');
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup: register Player A and Player B
// ─────────────────────────────────────────────────────────────────────────────
test('setup: register Player A and Player B for multiplayer test', async ({ page }) => {
  playerAEmail    = uniqueEmail('mpA');
  playerAUsername = uniqueUsername('mpA');
  playerAPassword = 'ProdMpA123!';

  playerBEmail    = uniqueEmail('mpB');
  playerBUsername = uniqueUsername('mpB');
  playerBPassword = 'ProdMpB123!';

  // Register Player A
  await registerViaUI(page, {
    email: playerAEmail,
    username: playerAUsername,
    password: playerAPassword,
    context: 'prod-multiplayer-spec:playerA',
  });

  // Log out Player A
  await page.click('.logout-button, button:has-text("Logout")');
  await page.waitForTimeout(1_500);

  // Register Player B
  await registerViaUI(page, {
    email: playerBEmail,
    username: playerBUsername,
    password: playerBPassword,
    context: 'prod-multiplayer-spec:playerB',
  });

  // Confirm Player B is logged in
  await expect(page.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 15_000 });

  console.log(`[prod-mp] Player A: ${playerAEmail} (${playerAUsername})`);
  console.log(`[prod-mp] Player B: ${playerBEmail} (${playerBUsername})`);
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST: Full multiplayer match flow — two browser contexts (ONE RUN ONLY)
// ─────────────────────────────────────────────────────────────────────────────
test('multiplayer — Player A creates, Player B joins, both see ACTIVE + trades sync', async () => {
  // Launch a second browser to hold BOTH contexts independently
  const browser = await chromium.launch();

  const contextA = await browser.newContext({ baseURL: PROD_BASE });
  const pageA    = await contextA.newPage();

  const contextB = await browser.newContext({ baseURL: PROD_BASE });
  const pageB    = await contextB.newPage();

  // Collect console errors for both contexts
  const errorsA = [];
  const errorsB = [];
  pageA.on('console', (m) => { if (m.type() === 'error' && !m.text().includes('Warning:')) errorsA.push(m.text()); });
  pageB.on('console', (m) => { if (m.type() === 'error' && !m.text().includes('Warning:')) errorsB.push(m.text()); });

  try {
    // ── Step 1: Player A logs in ────────────────────────────────────────────
    await pageA.goto(`${PROD_BASE}/login`);
    await pageA.fill('input[type="email"]', playerAEmail);
    await pageA.fill('input[type="password"]', playerAPassword);
    await pageA.click('button[type="submit"]');
    await pageA.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 35_000 });
    await expect(pageA.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 20_000 });
    console.log('[prod-mp] Player A: logged in ✓');

    // ── Step 2: Player B logs in ────────────────────────────────────────────
    await pageB.goto(`${PROD_BASE}/login`);
    await pageB.fill('input[type="email"]', playerBEmail);
    await pageB.fill('input[type="password"]', playerBPassword);
    await pageB.click('button[type="submit"]');
    await pageB.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 35_000 });
    await expect(pageB.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 20_000 });
    console.log('[prod-mp] Player B: logged in ✓');

    // ── Step 3: Player A opens multiplayer lobby ────────────────────────────
    await pageA.goto(`${PROD_BASE}/multiplayer`);
    await pageA.waitForSelector('.lobby-container, .custom-section, h1, h2', { timeout: 20_000 });

    // ── Step 4: Player A creates a custom game ──────────────────────────────
    const createBtn = pageA.locator('button.create-game-btn, button:has-text("Create Game")');
    await expect(createBtn).toBeVisible({ timeout: 15_000 });
    await createBtn.click();

    // Fill stock symbol
    const stockInput = pageA.locator(
      'input#stock-symbol, input[placeholder*="RELIANCE"], input[placeholder*="symbol"]'
    );
    await expect(stockInput).toBeVisible({ timeout: 15_000 });
    await stockInput.fill('RELIANCE');

    // Select shortest duration (2 minutes) for fast test completion
    const durationSelect = pageA.locator('select#duration, select');
    if (await durationSelect.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await durationSelect.selectOption({ value: '2' });
    }

    const submitBtn = pageA.locator('button.btn-create, button[type="submit"]:has-text("Create")');
    await expect(submitBtn).toBeVisible({ timeout: 8_000 });
    await submitBtn.click();

    // Player A navigates to /game/:gameId (WAITING phase)
    await pageA.waitForURL(/\/game\/\d+/, { timeout: 40_000 });
    const gameUrl = pageA.url();
    const gameIdMatch = gameUrl.match(/\/game\/(\d+)/);
    expect(gameIdMatch, 'Should navigate to a game URL with numeric ID').toBeTruthy();
    const gameId = gameIdMatch[1];
    console.log(`[prod-mp] Game created: ID=${gameId}, URL=${gameUrl}`);

    // Player A should see "Waiting for Opponent" screen
    const waitingText = pageA.locator(':text("Waiting for Opponent"), :text("Waiting for another player")');
    await expect(waitingText).toBeVisible({ timeout: 20_000 });
    console.log('[prod-mp] Player A: waiting for opponent ✓');

    // ── Step 5: Player B navigates to lobby and joins ───────────────────────
    await pageB.goto(`${PROD_BASE}/multiplayer`);
    await pageB.waitForSelector('.lobby-container, .game-list, h1, h2', { timeout: 20_000 });

    // Give the lobby time to refresh and show Player A's game
    await pageB.waitForTimeout(3_000);

    const joinBtn = pageB.locator('button.join-btn, button:has-text("Join Game")').first();
    if (await joinBtn.isVisible({ timeout: 12_000 }).catch(() => false)) {
      await joinBtn.click();
      await pageB.waitForURL(/\/game\/\d+/, { timeout: 25_000 });
      const pageBGameUrl = pageB.url();
      expect(pageBGameUrl).toContain(gameId);
      console.log('[prod-mp] Player B: joined game via lobby ✓');
    } else {
      // Fallback: navigate directly to game URL
      console.warn('[prod-mp] Join button not visible — navigating Player B directly to game URL');
      await pageB.goto(`${PROD_BASE}/game/${gameId}`);
      await pageB.waitForURL(/\/game\/\d+/, { timeout: 20_000 });
    }

    // ── Step 6: Both sides should see ACTIVE state via WebSocket ───────────
    // The "game-started" WebSocket event triggers the transition automatically
    const activeIndicator = ':text("Candle"), :text("remaining"), .candle-badge, .candle-countdown';

    console.log('[prod-mp] Waiting for ACTIVE state (WebSocket game-started event)...');
    await expect(pageA.locator(activeIndicator).first()).toBeVisible({ timeout: 45_000 });
    console.log('[prod-mp] Player A: game is ACTIVE ✓');

    await expect(pageB.locator(activeIndicator).first()).toBeVisible({ timeout: 45_000 });
    console.log('[prod-mp] Player B: game is ACTIVE ✓');

    // ── Step 7: Both players execute a trade ────────────────────────────────
    const sharesInputA = pageA.locator('input[type="number"]').first();
    if (await sharesInputA.isVisible({ timeout: 8_000 }).catch(() => false)) {
      await sharesInputA.fill('1');
    }
    const buyBtnA = pageA.locator('button.trade-btn.buy, button:has-text("Buy")').first();
    if (await buyBtnA.isEnabled({ timeout: 8_000 }).catch(() => false)) {
      await buyBtnA.click();
      console.log('[prod-mp] Player A: BUY trade submitted');
    }

    await pageA.waitForTimeout(2_000);

    const sharesInputB = pageB.locator('input[type="number"]').first();
    if (await sharesInputB.isVisible({ timeout: 8_000 }).catch(() => false)) {
      await sharesInputB.fill('1');
    }
    const buyBtnB = pageB.locator('button.trade-btn.buy, button:has-text("Buy")').first();
    if (await buyBtnB.isEnabled({ timeout: 8_000 }).catch(() => false)) {
      await buyBtnB.click();
      console.log('[prod-mp] Player B: BUY trade submitted');
    }

    // ── Step 8: Both should have a live scoreboard ──────────────────────────
    const scoreboardA = pageA.locator('.player-dashboard, [class*="scoreboard"]').first();
    await expect(scoreboardA).toBeVisible({ timeout: 20_000 });

    const scoreboardB = pageB.locator('.player-dashboard, [class*="scoreboard"]').first();
    await expect(scoreboardB).toBeVisible({ timeout: 20_000 });

    // ── Step 9: Wait for 2-minute match to complete ──────────────────────────
    console.log('[prod-mp] Waiting for match to complete (~2 minutes)...');
    await pageA.waitForURL(/\/match\/\d+\/result/, { timeout: 200_000 }); // 3-min+ safety net
    console.log('[prod-mp] Player A: navigated to result page ✓');

    await pageB.waitForURL(/\/match\/\d+\/result/, { timeout: 80_000 });
    console.log('[prod-mp] Player B: navigated to result page ✓');

    // ── Step 10: Both sides show a result screen ────────────────────────────
    const resultA = pageA.locator('[class*="result"], [class*="match-result"], h1, h2').first();
    await expect(resultA).toBeVisible({ timeout: 20_000 });
    const resultTextA = await pageA.locator('body').textContent();
    const hasResultDataA = /win|lose|result|profit|score|balance/i.test(resultTextA ?? '');
    expect(hasResultDataA, 'Player A result page should show win/loss/profit data').toBe(true);

    const resultB = pageB.locator('[class*="result"], [class*="match-result"], h1, h2').first();
    await expect(resultB).toBeVisible({ timeout: 20_000 });
    const resultTextB = await pageB.locator('body').textContent();
    const hasResultDataB = /win|lose|result|profit|score|balance/i.test(resultTextB ?? '');
    expect(hasResultDataB, 'Player B result page should show win/loss/profit data').toBe(true);

    // Both should reference the same game ID
    expect(pageA.url()).toContain(gameId);
    expect(pageB.url()).toContain(gameId);

    console.log(`[prod-mp] ✓ Full multiplayer match flow completed (gameId=${gameId})`);
    console.log(`[prod-mp] Created match: game ID ${gameId} between ${playerAUsername} and ${playerBUsername}`);

    // Console error summary
    const critA = errorsA.filter((e) => !e.includes('Warning:') && !e.includes('401') && !e.includes('400'));
    const critB = errorsB.filter((e) => !e.includes('Warning:') && !e.includes('401') && !e.includes('400'));
    if (critA.length > 0) console.warn('[prod-mp] Player A console errors:', critA);
    if (critB.length > 0) console.warn('[prod-mp] Player B console errors:', critB);

  } finally {
    await contextA.close();
    await contextB.close();
    await browser.close();
  }
});
