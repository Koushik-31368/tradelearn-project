// e2e/global-setup.js
// ─────────────────────────────────────────────────────────────────────────────
// Starts the full docker-compose e2e stack before Playwright runs any tests.
// Polls both backend (:8080/actuator/health) and frontend (:3000) until they
// respond or a hard timeout is reached.
//
// Set SKIP_DOCKER_START=true to skip (useful if you started the stack manually).
// ─────────────────────────────────────────────────────────────────────────────

const { execSync, spawn } = require('child_process');
const path = require('path');
const http = require('http');

const REPO_ROOT = path.resolve(__dirname, '..');
const COMPOSE_CMD = 'docker-compose';
const COMPOSE_FILES = [
  '-f', 'docker-compose.yml',
  '-f', 'docker-compose.e2e.yml',
];

const BACKEND_HEALTH_URL  = 'http://localhost:8080/actuator/health';
const FRONTEND_HEALTH_URL = 'http://localhost:3000';

/** Poll a URL until it returns 2xx or times out. */
async function pollUntilReady(url, label, timeoutMs = 180_000, intervalMs = 3_000) {
  const deadline = Date.now() + timeoutMs;
  process.stdout.write(`[setup] Waiting for ${label} (${url})...\n`);

  while (Date.now() < deadline) {
    const ok = await new Promise((resolve) => {
      const req = http.get(url, (res) => {
        resolve(res.statusCode >= 200 && res.statusCode < 400);
        res.resume();
      });
      req.on('error', () => resolve(false));
      req.setTimeout(2_000, () => { req.destroy(); resolve(false); });
    });

    if (ok) {
      process.stdout.write(`[setup] ${label} is ready ✓\n`);
      return;
    }
    await new Promise((r) => setTimeout(r, intervalMs));
  }

  throw new Error(`[setup] Timeout: ${label} (${url}) did not become healthy within ${timeoutMs / 1000}s`);
}

module.exports = async function globalSetup() {
  if (process.env.SKIP_DOCKER_START === 'true') {
    console.log('[setup] SKIP_DOCKER_START=true — skipping docker-compose startup');
    return;
  }

  console.log('[setup] Starting docker-compose e2e stack...');

  try {
    // Build + start detached. --wait would be nice but it only waits for
    // healthchecks on the *last* service; we'll poll ourselves for reliability.
    execSync(
      `${COMPOSE_CMD} ${COMPOSE_FILES.join(' ')} up --build -d`,
      {
        cwd: REPO_ROOT,
        stdio: 'inherit',
        env: { ...process.env },
      }
    );
  } catch (err) {
    throw new Error(`[setup] docker-compose up failed: ${err.message}`);
  }

  // Wait for backend health endpoint (up to 3 minutes — Spring Boot is slow)
  await pollUntilReady(BACKEND_HEALTH_URL, 'backend', 180_000);

  // Wait for frontend (nginx/CRA dev server)
  await pollUntilReady(FRONTEND_HEALTH_URL, 'frontend', 60_000);

  console.log('[setup] Full stack is ready. Starting tests.\n');
};
