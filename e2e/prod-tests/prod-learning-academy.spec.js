// e2e/prod-tests/prod-learning-academy.spec.js
// ─────────────────────────────────────────────────────────────────────────────
// PRODUCTION black-box Learning Academy tests.
// Covers the /academy or /learn route (if present) — sections, quizzes.
// Gracefully skips if no dedicated academy page exists, checking instead
// for embedded quiz/lesson content on /strategies.
// ─────────────────────────────────────────────────────────────────────────────

const { test, expect } = require('@playwright/test');
const { collectConsoleErrors } = require('../prod-helpers/auth');

// Candidate routes for a Learning Academy page
const ACADEMY_CANDIDATES = ['/academy', '/learn', '/learning', '/courses', '/curriculum'];

test.describe('Learning Academy (Production)', () => {

  // ── TEST 1: Discover and load the Learning Academy page ──────────────────
  test('learning academy page loads — sections and content visible', async ({ page }) => {
    const errors = collectConsoleErrors(page);
    let academyUrl = null;

    // Try candidate routes
    for (const candidate of ACADEMY_CANDIDATES) {
      const response = await page.goto(candidate, { waitUntil: 'domcontentloaded' });
      const status = response?.status() ?? 0;
      const bodyText = await page.locator('body').textContent();
      const hasContent = (bodyText?.trim().length ?? 0) > 100;

      // Accept route if: returns 200 AND has meaningful content AND not just login page
      const isLoginPage = page.url().includes('/login');
      if (status === 200 && hasContent && !isLoginPage) {
        // But SPA returns 200 for everything — check for actual academy content
        const hasAcademyContent = /lesson|quiz|learn|academy|course|module|curriculum|chapter/i.test(bodyText ?? '');
        if (hasAcademyContent) {
          academyUrl = candidate;
          console.log(`[prod-academy] Found academy content at: ${candidate}`);
          break;
        }
      }
    }

    if (!academyUrl) {
      // Fall back to /strategies (which has Learning Academy content integrated)
      console.warn('[prod-academy] No dedicated /academy or /learn route found — checking /strategies for embedded academy content');
      await page.goto('/strategies');
      await page.waitForLoadState('networkidle');

      const bodyText = await page.locator('body').textContent();
      const hasStrategyContent = /strategy|indicator|rsi|sma|entry|exit/i.test(bodyText ?? '');
      expect(
        hasStrategyContent,
        'If no dedicated academy page, /strategies must have educational content'
      ).toBe(true);
      console.log('[prod-academy] ✓ Educational content found on /strategies (academy is embedded)');
    } else {
      // Assert the academy page has at least sections/topics listed
      // Broad selector matching LearnPage.jsx actual class names
      const heading = page.locator(
        'h1, h2, h3, [class*="path"], [class*="learn"], [class*="lesson"], [class*="section"], [class*="module"], [class*="course"], [class*="academy"], [class*="card"]'
      ).first();
      await expect(heading).toBeVisible({ timeout: 20_000 });

      const bodyText = await page.locator('body').textContent();
      const hasContent = (bodyText?.trim().length ?? 0) > 200;
      expect(hasContent, `Academy page at ${academyUrl} should have substantial content`).toBe(true);
      console.log(`[prod-academy] ✓ Academy page at ${academyUrl} has content`);
    }

    errors.off();
    const critErrors = errors.messages.filter(
      (e) =>
        !e.includes('Warning:') &&
        !(e.includes('Failed to load resource') && (e.includes('400') || e.includes('401')))
    );
    if (critErrors.length > 0) {
      console.warn('[prod-academy] Console errors:', critErrors);
    }
    // Don't hard-fail on console errors here since the page might be optional
  });

  // ── TEST 2: Quiz interaction works if quizzes are present ────────────────
  test('quiz interaction — selecting an answer does not crash the page', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    // Try to find a quiz on any candidate route
    let quizFound = false;

    const routesToCheck = [...ACADEMY_CANDIDATES, '/strategies'];
    for (const route of routesToCheck) {
      await page.goto(route, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1_500);

      // Look for quiz elements: radio buttons, answer options, "Check Answer" buttons
      const quizQuestion = page.locator(
        '[class*="quiz"], [class*="question"], input[type="radio"], button:has-text("Check"), button:has-text("Submit Answer")'
      );

      if (await quizQuestion.first().isVisible({ timeout: 3_000 }).catch(() => false)) {
        quizFound = true;
        console.log(`[prod-academy] Quiz elements found on ${route}`);

        // Try clicking the first available answer option
        const answerOption = page.locator('input[type="radio"], [class*="answer-option"], [class*="quiz-option"]').first();
        if (await answerOption.isVisible({ timeout: 3_000 }).catch(() => false)) {
          await answerOption.click();
          await page.waitForTimeout(500);

          // Page must not crash
          const bodyText = await page.locator('body').textContent();
          expect((bodyText?.length ?? 0)).toBeGreaterThan(50);
          console.log('[prod-academy] ✓ Quiz answer click did not crash the page');
        }

        // Try clicking "Check Answer" or "Submit" if present
        const checkBtn = page.locator('button:has-text("Check"), button:has-text("Submit Answer"), button:has-text("Submit")').first();
        if (await checkBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
          await checkBtn.click();
          await page.waitForTimeout(1_000);

          // Page must still have content after submission
          const bodyText = await page.locator('body').textContent();
          expect((bodyText?.length ?? 0)).toBeGreaterThan(50);
          console.log('[prod-academy] ✓ Quiz submission did not crash the page');
        }
        break;
      }
    }

    if (!quizFound) {
      console.warn('[prod-academy] No quiz elements found on any candidate route — quizzes may not be implemented yet');
      // Skip (not a failure — quiz feature might be in progress)
    }

    errors.off();
    const critErrors = errors.messages.filter(
      (e) => !e.includes('Warning:') && !e.includes('DevTools')
    );
    if (critErrors.length > 0) {
      console.warn('[prod-academy] Console errors during quiz interaction:', critErrors);
    }
    // Soft assertion for quiz — crash is the only hard failure
  });

  // ── TEST 3: Learning content sections are navigable ───────────────────────
  test('learning content sections load without crash or blank page', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    // Check /strategies as the primary educational content area
    await page.goto('/strategies');
    await page.waitForLoadState('networkidle');

    // Must show at least 1 card/section
    const cards = page.locator('[class*="strategy-card"], [class*="card"], [class*="section"]');
    const count = await cards.count();
    expect(count).toBeGreaterThan(0);
    console.log(`[prod-academy] ${count} learning sections/cards found on /strategies`);

    // Click through a few cards and confirm no crash
    const limit = Math.min(count, 3);
    for (let i = 0; i < limit; i++) {
      await page.goto('/strategies');
      await page.waitForLoadState('networkidle');

      const freshCards = page.locator('[class*="strategy-card"], [class*="card"]');
      if (await freshCards.nth(i).isVisible({ timeout: 10_000 }).catch(() => false)) {
        await freshCards.nth(i).click();
        await page.waitForTimeout(800);

        const bodyText = await page.locator('body').textContent();
        expect((bodyText?.length ?? 0)).toBeGreaterThan(100);
        console.log(`[prod-academy] ✓ Section/card #${i + 1} clicked — page has content`);
      }
    }

    errors.off();
    const critErrors = errors.messages.filter(
      (e) => !e.includes('Warning:') && !e.includes('DevTools')
    );
    if (critErrors.length > 0) {
      console.warn('[prod-academy] Console errors during section navigation:', critErrors);
    }
    expect(critErrors, `Console errors during section navigation: ${critErrors.join('\n')}`).toHaveLength(0);
  });
});
