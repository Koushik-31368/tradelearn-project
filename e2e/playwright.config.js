// e2e/playwright.config.js
// ─────────────────────────────────────────────────────────────────────────────
// Playwright configuration for TradeLearn black-box E2E tests.
//
// Prerequisites before running:
//   1. Docker Desktop is running
//   2. Ports 3000, 8080, 5433, 6379 are free
//   3. Run:  cd e2e && npm install && npx playwright install --with-deps chromium
//   4. Run:  npm test  (starts docker-compose automatically via globalSetup)
//
// To run the full stack manually first and skip auto-start:
//   Set environment variable SKIP_DOCKER_START=true
// ─────────────────────────────────────────────────────────────────────────────

const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  // ── Test locations ────────────────────────────────────────────────────────
  testDir: './tests',

  // ── Parallelism ───────────────────────────────────────────────────────────
  // Run spec files in parallel, but tests within a file run sequentially.
  // Multiplayer tests require sequential ordering (player A then B).
  fullyParallel: false,
  workers: 1,         // single worker for predictable ordering; bump to 2+ for non-WS tests
  retries: 1,         // retry once on flake (network/timing issues)

  // ── Reporting ─────────────────────────────────────────────────────────────
  reporter: [
    ['list'],         // concise per-test output in terminal
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],

  // ── Global setup / teardown ───────────────────────────────────────────────
  globalSetup: './global-setup.js',
  globalTeardown: './global-teardown.js',

  // ── Shared test options ───────────────────────────────────────────────────
  use: {
    baseURL: 'http://localhost:3000',

    // Capture artifacts on failure
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'retain-on-failure',

    // Navigation timeout (SPA loads can take a moment)
    navigationTimeout: 30_000,
    actionTimeout: 15_000,
  },

  // ── Output folder for test artifacts ─────────────────────────────────────
  outputDir: './test-results',

  // ── Test timeout ─────────────────────────────────────────────────────────
  timeout: 60_000,   // 60s per test (generous for WS-heavy multiplayer tests)
  expect: {
    timeout: 10_000,
  },

  // ── Projects (browsers) ──────────────────────────────────────────────────
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    // Uncomment for cross-browser coverage:
    // { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    // { name: 'webkit',  use: { ...devices['Desktop Safari'] } },
  ],
});
