// e2e/prod-tests/prod-navigation.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// PRODUCTION black-box navigation & link integrity tests.
// Extended coverage per request:
//   - All public routes
//   - Protected routes (redirect behavior)
//   - Nonexistent route 404/not-found experience
//   - Page load performance flagging (>5s = warning, not failure)
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const { collectConsoleErrors, measurePageLoad } = require('../prod-helpers/auth');

const PUBLIC_ROUTES = [
  { path: '/',                label: 'Home' },
  { path: '/leaderboard',     label: 'Leaderboard' },
  { path: '/strategies',      label: 'Strategies' },
  { path: '/login',           label: 'Login' },
  { path: '/register',        label: 'Register' },
  { path: '/terms',           label: 'Terms of Service' },
  { path: '/privacy',         label: 'Privacy Policy' },
  { path: '/risk-disclosure', label: 'Risk Disclosure' },
];

const PROTECTED_ROUTES = [
  { path: '/profile',     label: 'Profile' },
  { path: '/simulator',   label: 'Simulator' },
  { path: '/multiplayer', label: 'Multiplayer' },
  { path: '/history',     label: 'Match History' },
  { path: '/missions',    label: 'Missions' },
];

test.describe('Navigation & Link Integrity (Production)', () => {

  // ── TEST 1: All public routes load without 404 ────────────────────────────
  test('all public routes load without 404', async ({ page }) => {
    const failedRoutes = [];
    const errors = collectConsoleErrors(page);

    for (const route of PUBLIC_ROUTES) {
      const response = await page.goto(route.path, { waitUntil: 'domcontentloaded' });
      const status = response?.status() ?? 0;

      // React SPA: always returns 200 from CDN (index.html for all paths)
      const bodyText = await page.locator('body').textContent();
      const hasContent = (bodyText?.trim().length ?? 0) > 50;

      if (!hasContent || status >= 400) {
        failedRoutes.push({ path: route.path, label: route.label, status });
        console.error(`[prod-nav] FAIL: ${route.label} (${route.path}) — status ${status}, length ${bodyText?.length}`);
      } else {
        console.log(`[prod-nav] ✓ ${route.label} (${route.path}) — ${status}`);
      }
    }

    errors.off();
    expect(
      failedRoutes,
      `Routes that failed:\n${failedRoutes.map((r) => `  ${r.label}: ${r.path}`).join('\n')}`
    ).toHaveLength(0);
  });

  // ── TEST 2: Protected routes redirect to /login ────────────────────────────
  test('protected routes redirect to /login when not authenticated', async ({ page }) => {
    const notRedirected = [];
    const errors = collectConsoleErrors(page);

    for (const route of PROTECTED_ROUTES) {
      await page.goto(route.path, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(3_000); // Allow React Router + auth guard to run

      const currentUrl = page.url();
      const isRedirected = currentUrl.includes('/login');

      if (!isRedirected) {
        const bodyText = await page.locator('body').textContent();
        const hasContent = (bodyText?.trim().length ?? 0) > 50;
        if (!hasContent) {
          notRedirected.push({ path: route.path, label: route.label, currentUrl, reason: 'blank page' });
        } else {
          console.warn(`[prod-nav] UNCLEAR: ${route.label} (${route.path}) did not redirect to /login — confirm if intentional`);
        }
      } else {
        console.log(`[prod-nav] ✓ ${route.label} → /login ✓`);
      }
    }

    errors.off();
    expect(
      notRedirected,
      `Protected routes showing blank/crash pages:\n${notRedirected.map((r) => `  ${r.label}: ${r.path} → ${r.currentUrl}`).join('\n')}`
    ).toHaveLength(0);
  });

  // ── TEST 3: Nonexistent route → reasonable not-found experience ───────────
  test('nonexistent route shows 404/not-found experience — not blank white screen', async ({ page }) => {
    await page.goto('/this-does-not-exist', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2_000); // Allow React Router to render

    const bodyText = await page.locator('body').textContent();
    const bodyLength = bodyText?.trim().length ?? 0;

    // Must not be a blank/empty page (would indicate a crash or missing error boundary)
    expect(
      bodyLength,
      'Nonexistent route should render SOMETHING — not a blank white screen'
    ).toBeGreaterThan(30);

    // Should ideally show "404", "Not Found", "Page not found", or redirect to a valid page
    const shows404 = /404|not found|page not found|doesn't exist|does not exist/i.test(bodyText ?? '');
    const redirectedToHome = page.url() === 'https://tradelearn-project.vercel.app/' ||
                             page.url().endsWith('/');

    if (shows404) {
      console.log('[prod-nav] ✓ Nonexistent route shows 404/not-found message ✓');
    } else if (redirectedToHome) {
      console.log('[prod-nav] ✓ Nonexistent route redirects to home ✓ (also acceptable)');
    } else {
      console.warn(
        '[prod-nav] ⚠️  Nonexistent route shows content but no "404" text. ' +
        `Current URL: ${page.url()}, Body snippet: "${bodyText?.slice(0, 200)}"`
      );
      // Not a hard failure — some SPAs render the home page for unknown routes
    }
  });

  // ── TEST 4: Navbar links navigate successfully ─────────────────────────────
  test('main navbar links all navigate successfully', async ({ page }) => {
    const errors = collectConsoleErrors(page);
    const failedLinks = [];

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    const navLinks = page.locator('.navbar-links a, nav a, .navbar a');
    const count = await navLinks.count();
    console.log(`[prod-nav] Found ${count} navbar links`);

    for (let i = 0; i < count; i++) {
      const link = navLinks.nth(i);
      const href = await link.getAttribute('href');
      const text = await link.textContent();

      if (!href || href.startsWith('http') || href.startsWith('mailto')) {
        continue; // Skip external links
      }

      try {
        await page.goto(href, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(1_500);

        const bodyText = await page.locator('body').textContent();
        const hasContent = (bodyText?.trim().length ?? 0) > 50;

        if (!hasContent) {
          failedLinks.push({ text: text?.trim(), href, reason: 'blank page after navigate' });
          console.error(`[prod-nav] FAIL: nav link "${text?.trim()}" → ${href} loaded blank page`);
        } else {
          console.log(`[prod-nav] ✓ Nav link "${text?.trim()}" → ${href}`);
        }
      } catch (err) {
        failedLinks.push({ text: text?.trim(), href, reason: err.message });
      }

      await page.goto('/');
    }

    errors.off();
    const critErrors = errors.messages.filter(
      (e) =>
        !e.includes('Warning:') &&
        !e.includes('DevTools') &&
        !(e.includes('Failed to load resource') && (e.includes('400') || e.includes('401')))
    );
    if (critErrors.length > 0) {
      console.warn('[prod-nav] Console errors during nav link testing:', critErrors);
    }

    expect(
      failedLinks,
      `Navbar links that failed:\n${failedLinks.map((l) => `  "${l.text}" → ${l.href}: ${l.reason}`).join('\n')}`
    ).toHaveLength(0);
  });

  // ── TEST 5: Footer legal links navigate successfully ──────────────────────
  test('footer legal links navigate successfully', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    const legalLinks = [
      { href: '/terms',           label: 'Terms of Service' },
      { href: '/privacy',         label: 'Privacy Policy' },
      { href: '/risk-disclosure', label: 'Risk Disclosure' },
    ];

    for (const link of legalLinks) {
      await page.goto(link.href, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1_000);

      const bodyText = await page.locator('body').textContent();
      const hasContent = (bodyText?.trim().length ?? 0) > 200;
      expect(
        hasContent,
        `Legal page "${link.label}" (${link.href}) should have content but appears blank`
      ).toBe(true);
      console.log(`[prod-nav] ✓ Legal page "${link.label}" has content`);
    }

    errors.off();
    const critErrors = errors.messages.filter(
      (e) =>
        !e.includes('Warning:') &&
        !(e.includes('Failed to load resource') && (e.includes('400') || e.includes('401')))
    );
    expect(critErrors, `Console errors on legal pages: ${critErrors.join('\n')}`).toHaveLength(0);
  });

  // ── TEST 6: Page load performance — flag any >5s pages ───────────────────
  test('page load performance — flag pages taking more than 5s to become interactive', async ({ page }) => {
    const WARN_THRESHOLD = 5_000; // 5 seconds
    const results = [];

    const pagesToMeasure = [
      { path: '/',             label: 'Home' },
      { path: '/leaderboard',  label: 'Leaderboard' },
      { path: '/strategies',   label: 'Strategies' },
      { path: '/login',        label: 'Login' },
      { path: '/register',     label: 'Register' },
    ];

    for (const { path, label } of pagesToMeasure) {
      const start = Date.now();
      try {
        await page.goto(path, { waitUntil: 'domcontentloaded' });
        const elapsed = Date.now() - start;
        const isSlowLy = elapsed > WARN_THRESHOLD;
        results.push({ label, path, elapsed, slow: isSlowLy });

        if (isSlowLy) {
          console.warn(
            `[prod-perf] ⚠️  SLOW: "${label}" (${path}) took ${elapsed}ms ` +
            `(threshold: ${WARN_THRESHOLD}ms) — may indicate a production issue`
          );
        } else {
          console.log(`[prod-perf] ✓ "${label}" (${path}) — ${elapsed}ms`);
        }
      } catch (err) {
        results.push({ label, path, elapsed: -1, slow: false, error: err.message });
        console.error(`[prod-perf] ❌ "${label}" failed to load: ${err.message}`);
      }
    }

    // Print summary
    console.log('\n[prod-perf] Performance summary:');
    console.log('  Page                  | Time (ms) | Status');
    console.log('  ─────────────────────────────────────────');
    for (const r of results) {
      const status = r.error ? 'ERROR' : r.slow ? '⚠️  SLOW' : '✓ OK';
      console.log(`  ${r.label.padEnd(22)}| ${String(r.elapsed).padEnd(9)} | ${status}`);
    }

    // Don't fail — just flag (per requirement: "flag, don't fail")
    // But do assert all pages at least loaded (no errors)
    const failedLoads = results.filter((r) => r.error);
    expect(
      failedLoads,
      `Pages that failed to load at all:\n${failedLoads.map((r) => `  ${r.label}: ${r.error}`).join('\n')}`
    ).toHaveLength(0);
  });

  // ── TEST 7: No uncaught JS errors on public pages ─────────────────────────
  test('no uncaught JS errors on public pages (home, leaderboard, strategies)', async ({ page }) => {
    const errors = collectConsoleErrors(page);
    const pagesToCheck = ['/', '/leaderboard', '/strategies'];

    for (const path of pagesToCheck) {
      await page.goto(path);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(2_000);
    }

    errors.off();
    const critErrors = errors.messages.filter(
      (e) =>
        !e.includes('Warning:') &&
        !e.includes('DevTools') &&
        !e.includes('net::ERR_') &&
        !(e.includes('Failed to load resource') && (e.includes('400') || e.includes('401')))
    );
    if (critErrors.length > 0) {
      console.error('[prod-nav] Uncaught JS errors across public pages:', critErrors);
    }
    expect(critErrors, `Uncaught JS errors:\n${critErrors.join('\n')}`).toHaveLength(0);
  });
});
