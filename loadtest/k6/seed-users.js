/**
 * TradeLearn — Test Data Seeding Script (k6)
 *
 * Pre-seeds 10,000 users via the /api/auth/register endpoint.
 * Run this BEFORE the main load test.
 *
 * Usage:
 *   k6 run -e BASE_URL=http://tradelearn-api.loadtest.svc.cluster.local:8080 \
 *          --vus 50 --iterations 10000 \
 *          seed-users.js
 */

import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";
import exec from "k6/execution";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const TOTAL_USERS = parseInt(__ENV.TOTAL_USERS || "10000");

const seeded = new Counter("users_seeded");
const failed = new Counter("users_failed");

export const options = {
  scenarios: {
    seed: {
      executor: "per-vu-iterations",
      vus: 50,
      iterations: TOTAL_USERS / 50,
      maxDuration: "10m",
    },
  },
  thresholds: {
    users_seeded: [`count>=${TOTAL_USERS * 0.99}`], // 99% success
  },
};

export default function () {
  const vuId = exec.vu.idInTest;
  const iterInVu = exec.vu.iterationInScenario;
  const globalIdx = (vuId - 1) * (TOTAL_USERS / 50) + iterInVu;

  if (globalIdx >= TOTAL_USERS) return;

  const user = {
    email: `loadtest_user_${globalIdx}@tradelearn.test`,
    username: `lt_user_${globalIdx}`,
    password: `LoadTest1${globalIdx}`,
  };

  const res = http.post(
    `${BASE_URL}/api/auth/register`,
    JSON.stringify(user),
    {
      headers: { "Content-Type": "application/json" },
      tags: { name: "seed_register" },
    }
  );

  const ok = check(res, {
    "registered or exists": (r) =>
      r.status === 200 || r.status === 201 || r.status === 409,
  });

  if (ok) {
    seeded.add(1);
  } else {
    failed.add(1);
    if (globalIdx < 5) {
      console.error(
        `Seed failed for user ${globalIdx}: status=${res.status} body=${res.body}`
      );
    }
  }
}
