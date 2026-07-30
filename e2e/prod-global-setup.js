// e2e/prod-global-setup.js
// ─────────────────────────────────────────────────────────────────────────────
// Global setup for PRODUCTION black-box tests.
// Does NOT spin up Docker. Instead:
//  1. Verifies the Vercel frontend is reachable
//  2. Warms up the Render backend (free-tier cold start can take 30-90s)
//  3. Confirms backend health before tests begin
// ─────────────────────────────────────────────────────────────────────────────

const https = require('https');

const FRONTEND_URL = 'https://tradelearn-project.vercel.app';
const BACKEND_HEALTH_URL = 'https://tradelearn-project-g.onrender.com/actuator/health';
const WARMUP_TIMEOUT_MS = 90_000;  // 90s — Render free-tier worst case

/**
 * Make a single HTTPS GET and return the status code + body.
 */
function httpGet(url, timeoutMs = 30_000) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, { timeout: timeoutMs }, (res) => {
      let body = '';
      res.on('data', (chunk) => (body += chunk));
      res.on('end', () => resolve({ status: res.statusCode, body }));
    });
    req.on('error', reject);
    req.on('timeout', () => {
      req.destroy();
      reject(new Error(`Request timed out after ${timeoutMs}ms`));
    });
  });
}

/**
 * Poll the backend health endpoint until it returns 200 or timeout elapses.
 * Render free-tier pods take 30-90s to cold-start.
 */
async function warmupBackend(timeoutMs = WARMUP_TIMEOUT_MS) {
  const start = Date.now();
  let attempts = 0;

  console.log(`\n[prod-setup] Warming up Render backend: ${BACKEND_HEALTH_URL}`);
  console.log(`[prod-setup] Max wait: ${timeoutMs / 1000}s (Render free-tier cold start)\n`);

  while (Date.now() - start < timeoutMs) {
    attempts++;
    try {
      const { status, body } = await httpGet(BACKEND_HEALTH_URL, 20_000);
      const elapsed = ((Date.now() - start) / 1000).toFixed(1);

      if (status === 200) {
        let healthData;
        try { healthData = JSON.parse(body); } catch (_) { healthData = body; }
        const backendStatus = healthData?.status ?? 'UNKNOWN';
        console.log(`[prod-setup] ✅ Backend UP (attempt ${attempts}, ${elapsed}s) — status: ${backendStatus}`);
        return true;
      } else {
        console.log(`[prod-setup] ⏳ Backend returned HTTP ${status} (attempt ${attempts}, ${elapsed}s) — retrying...`);
      }
    } catch (err) {
      const elapsed = ((Date.now() - start) / 1000).toFixed(1);
      console.log(`[prod-setup] ⏳ Backend not ready yet (attempt ${attempts}, ${elapsed}s): ${err.message} — retrying...`);
    }

    // Wait 5s between attempts
    await new Promise((r) => setTimeout(r, 5_000));
  }

  throw new Error(
    `[prod-setup] ❌ Backend did not become healthy within ${timeoutMs / 1000}s after ${attempts} attempts. ` +
    `Check Render dashboard: ${BACKEND_HEALTH_URL}`
  );
}

/**
 * Check that the Vercel frontend returns 200 with HTML content.
 */
async function checkFrontend() {
  console.log(`[prod-setup] Checking Vercel frontend: ${FRONTEND_URL}`);
  try {
    const { status, body } = await httpGet(FRONTEND_URL, 30_000);
    if (status === 200 && body.includes('<html')) {
      console.log(`[prod-setup] ✅ Frontend OK (HTTP ${status})`);
      return true;
    } else {
      console.error(`[prod-setup] ❌ Frontend returned unexpected response: HTTP ${status}`);
      console.error(`[prod-setup]    Body preview: ${body.slice(0, 200)}`);
      return false;
    }
  } catch (err) {
    console.error(`[prod-setup] ❌ Frontend unreachable: ${err.message}`);
    return false;
  }
}

module.exports = async function globalSetup() {
  console.log('\n' + '═'.repeat(70));
  console.log('  TradeLearn PRODUCTION E2E — Global Setup');
  console.log('═'.repeat(70) + '\n');

  // 1. Check frontend
  const frontendOk = await checkFrontend();
  if (!frontendOk) {
    throw new Error(
      '[prod-setup] Vercel frontend is not reachable. Aborting test run. ' +
      `Check: ${FRONTEND_URL}`
    );
  }

  // 2. Warm up backend (handles cold-start)
  await warmupBackend();

  console.log('\n[prod-setup] ✅ Both frontend and backend are reachable. Starting tests.\n');
  console.log('═'.repeat(70) + '\n');
};
