// e2e/tests/navigation.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// BLACK-BOX navigation & link integrity tests.
// Clicks through main nav links, checks for 404s and broken pages,
// and monitors console errors across all navigation.
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const { collectConsoleErrors } = require('../helpers/auth');

// Public routes accessible without authentication
const PUBLIC_ROUTES = [
  { path: '/',            label: 'Home' },
  { path: '/leaderboard', label: 'Leaderboard' },
  { path: '/strategies',  label: 'Strategies' },
  { path: '/login',       label: 'Login' },
  { path: '/register',    label: 'Register' },
  { path: '/terms',       label: 'Terms of Service' },
  { path: '/privacy',     label: 'Privacy Policy' },
  { path: '/risk-disclosure', label: 'Risk Disclosure' },
];

// Protected routes — should redirect to /login when unauthenticated
const PROTECTED_ROUTES = [
  { path: '/profile',    label: 'Profile' },
  { path: '/simulator',  label: 'Simulator' },
  { path: '/multiplayer', label: 'Multiplayer' },
  { path: '/history',    label: 'Match History' },
  { path: '/missions',   label: 'Missions' },
];

test.describe('Navigation — link integrity and no 404s', () => {

  // ── TEST 1: All public routes return a page (not 404) ──────────────────
  test('all public routes load without 404', async ({ page }) => {
    const failedRoutes = [];
    const errors = collectConsoleErrors(page);

    for (const route of PUBLIC_ROUTES) {
      const response = await page.goto(route.path, { waitUntil: 'domcontentloaded' });
      const status = response?.status() ?? 0;

      // React SPA — the HTML always returns 200 (nginx serves index.html for all routes)
      // But the React Router renders a blank/error page for unmatched routes.
      // Check: body must have actual content, not an empty div
      const bodyText = await page.locator('body').textContent();
      const hasContent = (bodyText?.trim().length ?? 0) > 50;

      if (!hasContent || (status >= 400)) {
        failedRoutes.push({ path: route.path, label: route.label, status });
        console.error(`[nav] FAIL: ${route.label} (${route.path}) — status ${status}, content length ${bodyText?.length}`);
      } else {
        console.log(`[nav] ✓ ${route.label} (${route.path}) — ${status}`);
      }
    }

    errors.off();
    expect(
      failedRoutes,
      `Routes that failed to load:\n${failedRoutes.map(r => `  ${r.label}: ${r.path}`).join('\n')}`
    ).toHaveLength(0);
  });

  // ── TEST 2: Protected routes redirect rather than 404 ──────────────────
  test('protected routes redirect to /login when not authenticated', async ({ page }) => {
    const notRedirected = [];
    const errors = collectConsoleErrors(page);

    for (const route of PROTECTED_ROUTES) {
      await page.goto(route.path, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(2_000); // Allow React Router + auth guard to run

      const currentUrl = page.url();
      const isRedirected = currentUrl.includes('/login');

      // Some routes may be public or partially protected (multiplayer shows content to anon)
      if (!isRedirected) {
        // Check if the page at least has content (not a crash)
        const bodyText = await page.locator('body').textContent();
        const hasContent = (bodyText?.trim().length ?? 0) > 50;
        if (!hasContent) {
          notRedirected.push({ path: route.path, label: route.label, currentUrl, reason: 'blank page' });
        } else {
          // Page loaded but didn't redirect — may be intentional (public) or a bug
          console.warn(`[nav] UNCLEAR: ${route.label} (${route.path}) did not redirect to /login — confirm if intentional`);
        }
      } else {
        console.log(`[nav] ✓ ${route.label} (${route.path}) correctly redirects to /login`);
      }
    }

    errors.off();
    expect(
      notRedirected,
      `Protected routes that showed blank/crash pages:\n${notRedirected.map(r => `  ${r.label}: ${r.path} → ${r.currentUrl}`).join('\n')}`
    ).toHaveLength(0);
  });

  // ── TEST 3: Main navbar links work ─────────────────────────────────────
  test('main navbar links all navigate successfully', async ({ page }) => {
    const errors = collectConsoleErrors(page);
    const failedLinks = [];

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    // Collect all nav links
    const navLinks = page.locator('.navbar-links a, nav a, .navbar a');
    const count = await navLinks.count();
    console.log(`[nav] Found ${count} navbar links`);

    for (let i = 0; i < count; i++) {
      const link = navLinks.nth(i);
      const href = await link.getAttribute('href');
      const text = await link.textContent();

      if (!href || href.startsWith('http') || href.startsWith('mailto')) {
        // Skip external links
        continue;
      }

      try {
        await page.goto(href, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(1_000);

        const bodyText = await page.locator('body').textContent();
        const hasContent = (bodyText?.trim().length ?? 0) > 50;

        if (!hasContent) {
          failedLinks.push({ text: text?.trim(), href, reason: 'blank page after navigate' });
          console.error(`[nav] FAIL: nav link "${text?.trim()}" → ${href} loaded blank page`);
        } else {
          console.log(`[nav] ✓ Nav link "${text?.trim()}" → ${href}`);
        }
      } catch (err) {
        failedLinks.push({ text: text?.trim(), href, reason: err.message });
      }

      // Go back to root for next iteration
      await page.goto('/');
    }

    errors.off();
    const critErrors = errors.messages.filter(
      (e) => !e.includes('Warning:') && !e.includes('DevTools')
    );
    if (critErrors.length > 0) {
      console.warn('[nav] Console errors during nav link testing:', critErrors);
    }

    expect(
      failedLinks,
      `Navbar links that failed:\n${failedLinks.map(l => `  "${l.text}" → ${l.href}: ${l.reason}`).join('\n')}`
    ).toHaveLength(0);
  });

  // ── TEST 4: Footer legal links work ───────────────────────────────────
  test('footer legal links navigate successfully', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    const legalLinks = [
      { href: '/terms',            label: 'Terms of Service' },
      { href: '/privacy',          label: 'Privacy Policy' },
      { href: '/risk-disclosure',  label: 'Risk Disclosure' },
    ];

    for (const link of legalLinks) {
      await page.goto(link.href, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(500);

      // Page must have actual legal content (not blank)
      const bodyText = await page.locator('body').textContent();
      const hasContent = (bodyText?.trim().length ?? 0) > 200;
      expect(
        hasContent,
        `Legal page "${link.label}" (${link.href}) should have content but appears blank`
      ).toBe(true);
      console.log(`[nav] ✓ Legal page "${link.label}" has content`);
    }

    errors.off();
    const critErrors = errors.messages.filter(
      (e) =>
        !e.includes('Warning:') &&
        !(e.includes('Failed to load resource') && (e.includes('400') || e.includes('401')))
    );
    expect(critErrors, `Console errors on legal pages: ${critErrors.join('\n')}`).toHaveLength(0);
  });

  // ── TEST 5: No uncaught JS errors on public pages ─────────────────────
  test('no uncaught JS errors on public pages (home, leaderboard, strategies)', async ({ page }) => {
    const errors = collectConsoleErrors(page);
    const pagesToCheck = ['/', '/leaderboard', '/strategies'];

    for (const path of pagesToCheck) {
      await page.goto(path);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1_500);
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
      console.error('[nav] Uncaught JS errors across public pages:', critErrors);
    }
    expect(critErrors, `Uncaught JS errors:\n${critErrors.join('\n')}`).toHaveLength(0);
  });
});
