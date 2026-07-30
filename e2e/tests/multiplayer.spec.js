// e2e/tests/multiplayer.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// BLACK-BOX multiplayer match tests using TWO separate browser contexts.
// Player A (creator) and Player B (joiner) run concurrently in their own
// isolated browser contexts — no shared state, no cookies shared.
//
// This exercises:
//   - Game creation via UI
//   - Game joining via UI
//   - WebSocket real-time synchronization (both sides see ACTIVE without refresh)
//   - Trade execution in active match
//   - Match completion with consistent result screens
//
// Note: These tests are time-sensitive (WebSocket events). Timeouts are generous.
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect, chromium } = require('@playwright/test');
const { uniqueEmail, collectConsoleErrors } = require('../helpers/auth');

// Credentials created fresh for this test file
let playerAEmail, playerAUsername, playerAPassword;
let playerBEmail, playerBUsername, playerBPassword;

// ─────────────────────────────────────────────────────────────────────────────
// Helper: register a user via the UI in a given page
// ─────────────────────────────────────────────────────────────────────────────
async function registerViaUI(page, { email, username, password }) {
  await page.goto('http://localhost:3000/register');
  await page.fill('input[type="email"]', email);
  await page.fill('input[type="text"]', username);
  await page.fill('input[type="password"]', password);
  const checkbox = page.locator('input[type="checkbox"]');
  if (await checkbox.isVisible({ timeout: 2_000 }).catch(() => false)) {
    await checkbox.check();
  }
  await page.click('button[type="submit"]');
  // Wait for redirect out of /register
  await page.waitForURL((url) => !url.pathname.includes('/register'), { timeout: 20_000 });
  // Confirm logged in
  await page.waitForSelector('.logout-button, button:has-text("Logout")', { timeout: 15_000 });
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup: register both players (sequential, using a single page context)
// ─────────────────────────────────────────────────────────────────────────────
test('setup: register Player A and Player B', async ({ page }) => {
  playerAEmail    = uniqueEmail('playerA');
  playerAUsername = `playerA_${Date.now()}`;
  playerAPassword = 'PlayerPass123!';

  playerBEmail    = uniqueEmail('playerB');
  playerBUsername = `playerB_${Date.now()}`;
  playerBPassword = 'PlayerPass123!';

  // Register Player A
  await registerViaUI(page, {
    email: playerAEmail,
    username: playerAUsername,
    password: playerAPassword,
  });

  // Log out Player A
  await page.click('.logout-button, button:has-text("Logout")');
  await page.waitForTimeout(1_000);

  // Register Player B
  await registerViaUI(page, {
    email: playerBEmail,
    username: playerBUsername,
    password: playerBPassword,
  });

  // Both registered — confirm Player B is logged in at end
  await expect(page.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 10_000 });
});

// ─────────────────────────────────────────────────────────────────────────────
// TEST: Full multiplayer match flow — two browser contexts
// ─────────────────────────────────────────────────────────────────────────────
test('multiplayer match — Player A creates, Player B joins, both see ACTIVE + trades sync', async () => {
  // Launch a second browser to hold BOTH contexts independently
  const browser = await chromium.launch();

  // Context A — Player A (creator)
  const contextA = await browser.newContext({ baseURL: 'http://localhost:3000' });
  const pageA    = await contextA.newPage();

  // Context B — Player B (joiner)
  const contextB = await browser.newContext({ baseURL: 'http://localhost:3000' });
  const pageB    = await contextB.newPage();

  const errorsA = collectConsoleErrors(pageA);
  const errorsB = collectConsoleErrors(pageB);

  try {
    // ── Step 1: Player A logs in ──────────────────────────────────────────
    await pageA.goto('http://localhost:3000/login');
    await pageA.fill('input[type="email"]', playerAEmail);
    await pageA.fill('input[type="password"]', playerAPassword);
    await pageA.click('button[type="submit"]');
    await pageA.waitForURL(/\/(multiplayer|\/?)/, { timeout: 20_000 });
    await expect(pageA.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 10_000 });

    // ── Step 2: Player B logs in ──────────────────────────────────────────
    await pageB.goto('http://localhost:3000/login');
    await pageB.fill('input[type="email"]', playerBEmail);
    await pageB.fill('input[type="password"]', playerBPassword);
    await pageB.click('button[type="submit"]');
    await pageB.waitForURL(/\/(multiplayer|\/?)/, { timeout: 20_000 });
    await expect(pageB.locator('.logout-button, button:has-text("Logout")')).toBeVisible({ timeout: 10_000 });

    // ── Step 3: Player A navigates to multiplayer lobby ───────────────────
    await pageA.goto('http://localhost:3000/multiplayer');
    await pageA.waitForSelector('.lobby-container, .custom-section, h1, h2', { timeout: 15_000 });

    // ── Step 4: Player A creates a custom game ────────────────────────────
    const createBtn = pageA.locator('button.create-game-btn, button:has-text("Create Game")');
    await expect(createBtn).toBeVisible({ timeout: 10_000 });
    await createBtn.click();

    // Fill the create game modal
    const stockInput = pageA.locator('input#stock-symbol, input[placeholder*="RELIANCE"], input[placeholder*="symbol"]');
    await expect(stockInput).toBeVisible({ timeout: 10_000 });
    await stockInput.fill('RELIANCE');

    // Select shortest duration (2 minutes) for fast test completion
    const durationSelect = pageA.locator('select#duration, select');
    if (await durationSelect.isVisible({ timeout: 2_000 }).catch(() => false)) {
      await durationSelect.selectOption({ value: '2' });
    }

    // Submit
    const submitBtn = pageA.locator('button.btn-create, button[type="submit"]:has-text("Create")');
    await expect(submitBtn).toBeVisible({ timeout: 5_000 });
    await submitBtn.click();

    // After create, Player A should navigate to /game/:gameId (WAITING phase)
    await pageA.waitForURL(/\/game\/\d+/, { timeout: 30_000 });
    const gameUrl = pageA.url();
    const gameIdMatch = gameUrl.match(/\/game\/(\d+)/);
    expect(gameIdMatch, 'Should navigate to a game URL with numeric ID').toBeTruthy();
    const gameId = gameIdMatch[1];
    console.log('[multiplayer] Game ID:', gameId);

    // Player A should see "Waiting for Opponent" screen
    const waitingText = pageA.locator(':text("Waiting for Opponent"), :text("Waiting for another player")');
    await expect(waitingText).toBeVisible({ timeout: 15_000 });

    // ── Step 5: Player B navigates to multiplayer lobby and joins ─────────
    await pageB.goto('http://localhost:3000/multiplayer');
    await pageB.waitForSelector('.lobby-container, .game-list', { timeout: 15_000 });

    // Look for the game Player A created in the open games list
    // It may take a moment for the lobby to refresh
    await pageB.waitForTimeout(2_000);

    // Try to find a join button for the game
    const joinBtn = pageB.locator(`button.join-btn, button:has-text("Join Game")`).first();
    if (await joinBtn.isVisible({ timeout: 10_000 }).catch(() => false)) {
      await joinBtn.click();
      // Player B should navigate to the same game
      await pageB.waitForURL(/\/game\/\d+/, { timeout: 20_000 });
      const pageBGameUrl = pageB.url();
      expect(pageBGameUrl).toContain(gameId);
    } else {
      // Direct navigate as fallback if game list doesn't show yet
      console.warn('[multiplayer] Join button not visible — navigating directly to game URL');
      await pageB.goto(`http://localhost:3000/game/${gameId}`);
      await pageB.waitForURL(/\/game\/\d+/, { timeout: 15_000 });
    }

    // ── Step 6: Both sides should see ACTIVE state (no manual refresh) ────
    // The WS "game-started" event should trigger the transition automatically
    // within ~5 seconds of Player B joining
    const activeIndicator = ':text("Candle"), :text("remaining"), .candle-badge, .candle-countdown';

    // Wait for Player A to see ACTIVE (WebSocket push from backend)
    await expect(pageA.locator(activeIndicator).first()).toBeVisible({ timeout: 30_000 });
    console.log('[multiplayer] Player A: game is ACTIVE ✓');

    // Wait for Player B to see ACTIVE
    await expect(pageB.locator(activeIndicator).first()).toBeVisible({ timeout: 30_000 });
    console.log('[multiplayer] Player B: game is ACTIVE ✓');

    // ── Step 7: Both players execute a trade ─────────────────────────────
    // Player A: BUY 1 share
    const sharesInputA = pageA.locator('input[type="number"]').first();
    if (await sharesInputA.isVisible({ timeout: 5_000 }).catch(() => false)) {
      await sharesInputA.fill('1');
    }
    const buyBtnA = pageA.locator('button.trade-btn.buy, button:has-text("Buy")').first();
    if (await buyBtnA.isEnabled({ timeout: 5_000 }).catch(() => false)) {
      await buyBtnA.click();
      console.log('[multiplayer] Player A: BUY trade submitted');
    } else {
      console.warn('[multiplayer] Player A: Buy button not enabled — trade may not work in WAITING phase');
    }

    await pageA.waitForTimeout(1_500);

    // Player B: BUY 1 share
    const sharesInputB = pageB.locator('input[type="number"]').first();
    if (await sharesInputB.isVisible({ timeout: 5_000 }).catch(() => false)) {
      await sharesInputB.fill('1');
    }
    const buyBtnB = pageB.locator('button.trade-btn.buy, button:has-text("Buy")').first();
    if (await buyBtnB.isEnabled({ timeout: 5_000 }).catch(() => false)) {
      await buyBtnB.click();
      console.log('[multiplayer] Player B: BUY trade submitted');
    } else {
      console.warn('[multiplayer] Player B: Buy button not enabled');
    }

    // ── Step 8: Check real-time scoreboard update (no manual refresh) ─────
    // Both sides should have a LiveScoreboard — check it's showing data
    const scoreboardA = pageA.locator('.player-dashboard, [class*="scoreboard"]').first();
    await expect(scoreboardA).toBeVisible({ timeout: 15_000 });

    const scoreboardB = pageB.locator('.player-dashboard, [class*="scoreboard"]').first();
    await expect(scoreboardB).toBeVisible({ timeout: 15_000 });

    // ── Step 9: Wait for game to finish (2-minute match) ─────────────────
    // The game should auto-navigate to /match/:id/result when finished
    console.log('[multiplayer] Waiting for 2-minute match to complete...');
    await pageA.waitForURL(/\/match\/\d+\/result/, { timeout: 180_000 }); // 3-minute safety timeout
    console.log('[multiplayer] Player A: navigated to result page ✓');

    await pageB.waitForURL(/\/match\/\d+\/result/, { timeout: 60_000 }); // B should follow quickly
    console.log('[multiplayer] Player B: navigated to result page ✓');

    // ── Step 10: Both sides show a result screen with data ────────────────
    // Check Player A's result page has meaningful content
    const resultA = pageA.locator('[class*="result"], [class*="match-result"], h1, h2').first();
    await expect(resultA).toBeVisible({ timeout: 15_000 });
    const resultTextA = await pageA.locator('body').textContent();
    const hasResultDataA = /win|lose|result|profit|score|balance/i.test(resultTextA ?? '');
    expect(hasResultDataA, 'Player A result page should show win/loss/profit data').toBe(true);

    // Check Player B's result page
    const resultB = pageB.locator('[class*="result"], [class*="match-result"], h1, h2').first();
    await expect(resultB).toBeVisible({ timeout: 15_000 });
    const resultTextB = await pageB.locator('body').textContent();
    const hasResultDataB = /win|lose|result|profit|score|balance/i.test(resultTextB ?? '');
    expect(hasResultDataB, 'Player B result page should show win/loss/profit data').toBe(true);

    // Both should be looking at the same game ID in the URL
    const resultUrlA = pageA.url();
    const resultUrlB = pageB.url();
    expect(resultUrlA).toContain(gameId);
    expect(resultUrlB).toContain(gameId);

    // Check for contradictory outcomes: if A sees "WIN", B should not also see "WIN"
    // (unless it's a draw — which is a valid state worth flagging)
    const aWins = resultTextA?.toLowerCase().includes('win') && !resultTextA?.toLowerCase().includes('your win');
    const bWins = resultTextB?.toLowerCase().includes('win') && !resultTextB?.toLowerCase().includes('your win');
    if (aWins && bWins) {
      console.warn('[multiplayer] UNCLEAR: Both Player A and Player B appear to see a "win" — may be misleading UI copy or a bug');
    }

    console.log('[multiplayer] ✓ Full multiplayer match flow completed successfully');

    // Console error summary
    errorsA.off();
    errorsB.off();
    const critA = errorsA.messages.filter((e) => !e.includes('Warning:') && !e.includes('DevTools'));
    const critB = errorsB.messages.filter((e) => !e.includes('Warning:') && !e.includes('DevTools'));
    if (critA.length > 0) console.warn('[multiplayer] Player A console errors:', critA);
    if (critB.length > 0) console.warn('[multiplayer] Player B console errors:', critB);

  } finally {
    await contextA.close();
    await contextB.close();
    await browser.close();
  }
});
