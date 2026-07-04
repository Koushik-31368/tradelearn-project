// e2e/playwright.prod.config.js
// ─────────────────────────────────────────────────────────────────────────────
// Playwright configuration for BLACK-BOX E2E tests against the LIVE production
// deployment.
//
// Frontend:  https://tradelearn-project.vercel.app   (Vercel CDN)
// Backend:   https://tradelearn-project-g.onrender.com  (Render free-tier)
//
// Key differences from local config:
//  - No globalSetup/globalTeardown (no Docker spin-up needed)
//  - Longer timeouts (real network latency + Render cold-start margin)
//  - Custom globalSetup just warms up the Render backend before tests run
//  - Test accounts prefixed "e2e_test_" + timestamp for easy cleanup
//  - Tests run sequentially (workers:1) — don't hammer live WS infra
//
// Run with:
//   cd e2e && npm run test:prod
// Or directly:
//   npx playwright test --config=playwright.prod.config.js
// ─────────────────────────────────────────────────────────────────────────────

const { defineConfig, devices } = require('@playwright/test');

const PROD_FRONTEND = 'https://tradelearn-project.vercel.app';

module.exports = defineConfig({
  // ── Test locations ─────────────────────────────────────────────────────────
  testDir: './prod-tests',

  // ── Parallelism ────────────────────────────────────────────────────────────
  // Sequential — don't hammer live matchmaking / WebSocket infra with parallel load
  fullyParallel: false,
  workers: 1,
  retries: 1,   // one retry on flake (network hiccup, Render throttling)

  // ── Reporting ──────────────────────────────────────────────────────────────
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-prod-report', open: 'never' }],
    ['json', { outputFile: 'prod-test-results.json' }],
  ],

  // ── Global setup (backend warmup only — no Docker) ─────────────────────────
  globalSetup: './prod-global-setup.js',

  // ── Shared test options ────────────────────────────────────────────────────
  use: {
    baseURL: PROD_FRONTEND,

    // Production latency margins (Render + Vercel CDN + real network)
    navigationTimeout: 60_000,   // 60s  (vs 30s local)
    actionTimeout:     25_000,   // 25s  (vs 15s local)

    // Capture artifacts on failure
    screenshot: 'only-on-failure',
    video:      'retain-on-failure',
    trace:      'retain-on-failure',

    // Accept self-signed certs if Render serves one during cold-start
    ignoreHTTPSErrors: true,
  },

  // ── Output folder ──────────────────────────────────────────────────────────
  outputDir: './prod-test-results',

  // ── Test timeout ───────────────────────────────────────────────────────────
  // 120s per test — generous for slow Render responses + WS-heavy multiplayer
  timeout: 120_000,
  expect: {
    timeout: 20_000,   // 20s assertion wait (vs 10s local)
  },

  // ── Projects (browsers) ────────────────────────────────────────────────────
  projects: [
    {
      name: 'chromium-desktop',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1280, height: 800 },
      },
    },
    {
      name: 'chromium-mobile',
      use: {
        ...devices['Pixel 5'],
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,   // required for tap() to work in mobile context
      },
      // Mobile project only runs the targeted mobile viewport tests
      testMatch: '**/prod-mobile-viewport.spec.js',
    },
  ],
});
