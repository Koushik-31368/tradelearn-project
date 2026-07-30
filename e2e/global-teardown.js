// e2e/global-teardown.js
// ─────────────────────────────────────────────────────────────────────────────
// Tears down the docker-compose e2e stack after all Playwright tests finish.
// Uses `down -v` to remove containers AND named volumes (throwaway Postgres data).
// ─────────────────────────────────────────────────────────────────────────────

const { execSync } = require('child_process');
const path = require('path');

const REPO_ROOT = path.resolve(__dirname, '..');

module.exports = async function globalTeardown() {
  if (process.env.SKIP_DOCKER_START === 'true') {
    console.log('[teardown] SKIP_DOCKER_START=true — skipping docker-compose shutdown');
    return;
  }

  console.log('[teardown] Stopping and removing e2e docker-compose stack...');
  try {
    execSync(
      'docker-compose -f docker-compose.yml -f docker-compose.e2e.yml down -v --remove-orphans',
      {
        cwd: REPO_ROOT,
        stdio: 'inherit',
        env: { ...process.env },
      }
    );
    console.log('[teardown] Stack removed cleanly ✓');
  } catch (err) {
    // Non-fatal — don't mask test failures
    console.warn(`[teardown] Warning: docker-compose down failed: ${err.message}`);
  }
};
