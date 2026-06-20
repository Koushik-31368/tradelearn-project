# TradeLearn — Refactor Plan

> **Status:** PLANNING
> **Scope:** Full-stack repository restructure — no feature changes
> **Priority:** Infrastructure (safe to do before next feature sprint)

---

## Executive Summary

The repository has evolved from a prototype to a production system, but the folder structure hasn't kept pace. The backend uses a **package-by-layer** pattern (all controllers in one folder, all services in one folder) that creates confusion for new contributors. Several empty "placeholder" folders were created but never populated. The frontend already uses feature-based architecture and is largely clean. This plan formalizes both structures, removes dead code, and produces a repository that would pass a Series A technical review.

---

## Section 1: Current Problems

### 🔴 Critical

| ID | Problem | Location | Impact |
|---|---|---|---|
| B1 | **5 empty top-level packages**: `controller/`, `model/`, `repository/`, `service/`, `security/` exist at the `com.tradelearn.server` root but are entirely empty — dead directory stubs left from an earlier layer-based structure | `backend/src/main/java/com/tradelearn/server/` | Confuses every developer who opens the backend |
| B2 | **`socket/` package is empty** | `server/socket/` | Dead directory — WebSocket code already moved to `websocket/` |
| B3 | **Duplicate `socket` vs `websocket` concepts** — `websocket/` has all real code; `socket/` is empty | — | Naming confusion |
| B4 | **Global `dto/` package with 20 mixed DTOs** — contains DTOs for game, social, analytics, market mixed together | `server/dto/` | No clear ownership, difficult to find domain-specific DTOs |
| B5 | **`fix_imports.js` migration script** at root of `frontend/` — was used during refactor, is now dead weight | `frontend/fix_imports.js` | OSS anti-pattern, leaves evidence of messy migration history |
| B6 | **`tradelearn_architecture_audit.md` at repo root** — a 952-line audit document committed to the project root | Root | Should be a doc, not a root-level artifact |
| B7 | **`backend/PRODUCTION_ARCHITECTURE.md`** committed inside the backend source tree | `backend/` | Docs belong in `docs/`, not inside a service folder |
| B8 | **`backend/logs/` directory** — contains runtime log files committed to source control | `backend/logs/` | Log files must never be committed |

### 🟡 Moderate

| ID | Problem | Location | Impact |
|---|---|---|---|
| B9 | **`common/` sub-packages each have only 1 file** — `config/WebConfig.java` (1 file), `controller/HealthController.java` (1 file) | `server/common/` | Excessive subdirectory nesting for single-file packages |
| B10 | **`infrastructure/ratelimit/` has only 2 files** — `TradeProcessingPipeline` + `TradeRateLimiter` belong together but the pipeline arguably belongs in `game/` | `server/infrastructure/ratelimit/` | Minor naming ambiguity |
| B11 | **`infrastructure/pipeline/` has only 1 file** — `GameMetricsService` | `server/infrastructure/pipeline/` | Over-subdivided; should merge into `infrastructure/` |
| B12 | **Inconsistent Vercel config** — `vercel.json` exists at **both** root AND `frontend/` | Root + `frontend/vercel.json` | Deployment confusion, unclear which one Vercel reads |
| F1 | **`frontend/src/utils/` is empty** | `frontend/src/utils/` | Dead empty directory |
| F2 | **`frontend/src/data/` has a duplicate file** — `historicalEvents.js` appears in both `src/data/` AND `features/practice/data/` | `src/data/` + `features/practice/data/` | Import confusion, file divergence risk |
| F3 | **`frontend/src/features/simulator/data/` is empty** | `features/simulator/data/` | Stubs left behind |
| F4 | **`frontend/src/features/social/pages/` is empty** | `features/social/pages/` | Stubs left behind |
| F5 | **`frontend/src/features/auth/components/` is empty** | `features/auth/components/` | Stubs left behind |
| F6 | **`frontend/logo.svg`** at `frontend/` root — not referenced by `public/index.html` or any component | `frontend/logo.svg` | Orphaned asset |

### 🟢 Minor

| ID | Problem | Location | Impact |
|---|---|---|---|
| M1 | `loadtest/k8s/` has 1 file; `loadtest/monitoring/` has 1 file | `loadtest/` | Thin directories — acceptable but worth noting |
| M2 | `docker-compose.yml` and `docker-compose.dev.yml` at repo root — not grouped | Root | Minor organization issue |
| M3 | `vercel.json` at root (frontend-only config) alongside Docker configs | Root | Deployment config mixed with repo root |
| M4 | `.vscode/` settings committed | Root | Dev environment leakage (should be in `.gitignore`) |

---

## Section 2: Recommended Architecture

### Backend Target

```
server/ (com.tradelearn.server)
├── ServerApplication.java
├── auth/             ← complete domain (config, controller, security)
├── user/             ← complete domain (controller, model, repo, service)
├── game/             ← complete domain (controller, model, repo, service)
├── matchmaking/      ← complete domain (controller, service)
├── market/           ← complete domain (controller, provider, service)
├── leaderboard/      ← complete domain (controller, model, repo, service)
├── profile/          ← complete domain (controller, service)
├── learning/         ← complete domain (controller, model, repo, service)
├── quests/           ← complete domain (controller, model, repo, service)
├── social/           ← complete domain (controller, model, repo, service)
├── analytics/        ← complete domain (controller, service)
├── simulator/        ← complete domain (controller, model, repo, service)
├── websocket/        ← WebSocket infrastructure
├── infrastructure/   ← redis, resilience, scheduling, ratelimit
├── dto/              ← (keep for now — shared cross-domain DTOs)
└── common/           ← exception, middleware, util, validation
    ← FLATTEN: remove single-file sub-packages (config/, controller/)
    ← MOVE WebConfig.java → directly under auth/ or standalone
    ← MOVE HealthController.java → directly under common/
```

### Frontend Target

```
src/
├── app/              ← App.js, index.js (rename from current root)
├── features/         ← (already good — clean up empty dirs)
├── shared/           ← (move from layout/ → shared/components/)
├── api/              ← (already good)
├── hooks/            ← (already good)
└── assets/           ← (already good — move logo.svg here)
```

### Deployment Files Target

```
/ (repo root)
├── docker-compose.yml
├── docker-compose.dev.yml
├── vercel.json              ← KEEP here (Vercel reads from root)
├── .github/workflows/
├── README.md
├── CONTRIBUTING.md
├── ARCHITECTURE.md          ← NEW
├── REFACTOR_PLAN.md         ← NEW
└── docs/
    ├── architecture.md
    ├── PRODUCTION_ARCHITECTURE.md  ← MOVED from backend/
    ├── developer-setup.md
    ├── deployment.md
    ├── api-reference.md
    └── demo-accounts.md
```

---

## Section 3: Exact File Moves

### 3.1 Backend — DELETE (empty packages / dead files)

| Action | Path | Reason |
|---|---|---|
| **DELETE DIR** | `server/controller/` | Empty stub |
| **DELETE DIR** | `server/model/` | Empty stub |
| **DELETE DIR** | `server/repository/` | Empty stub |
| **DELETE DIR** | `server/service/` | Empty stub |
| **DELETE DIR** | `server/security/` | Empty stub |
| **DELETE DIR** | `server/socket/` | Empty stub (real code is in `websocket/`) |
| **DELETE DIR** | `server/exception/` | Empty stub |
| **DELETE DIR** | `server/middleware/` | Empty stub |
| **DELETE DIR** | `server/util/` | Empty stub |
| **DELETE DIR** | `server/validation/` | Empty stub |
| **DELETE DIR** | `server/config/` | Empty stub |
| **DELETE DIR** | `backend/logs/` | Runtime logs — add to `.gitignore` |

### 3.2 Backend — MOVE (common/ flattening)

> **Note:** Java package changes require updating `package` declarations and all `import` statements in affected files.

| Action | From | To |
|---|---|---|
| **MOVE** | `common/config/WebConfig.java` | `common/WebConfig.java` (flatten — 1 file doesn't need a subfolder) |
| **MOVE** | `common/controller/HealthController.java` | `common/HealthController.java` (flatten — 1 file doesn't need a subfolder) |
| **DELETE DIR** | `common/config/` | Now empty after move |
| **DELETE DIR** | `common/controller/` | Now empty after move |

### 3.3 Backend — MOVE (infrastructure/ cleanup)

| Action | From | To |
|---|---|---|
| **MERGE DIR** | `infrastructure/pipeline/GameMetricsService.java` | `infrastructure/scheduling/GameMetricsService.java` (scheduling/observability concern) |
| **DELETE DIR** | `infrastructure/pipeline/` | Now empty after merge |

### 3.4 Backend — MOVE (docs)

| Action | From | To |
|---|---|---|
| **MOVE** | `backend/PRODUCTION_ARCHITECTURE.md` | `docs/PRODUCTION_ARCHITECTURE.md` |

### 3.5 Frontend — DELETE (dead files/dirs)

| Action | Path | Reason |
|---|---|---|
| **DELETE** | `frontend/fix_imports.js` | Migration script — already completed, no longer needed |
| **DELETE** | `frontend/logo.svg` | Unreferenced orphaned asset (not in `public/` nor imported anywhere) |
| **DELETE DIR** | `frontend/src/utils/` | Empty directory |
| **DELETE DIR** | `frontend/src/features/simulator/data/` | Empty directory |
| **DELETE DIR** | `frontend/src/features/social/pages/` | Empty directory |
| **DELETE DIR** | `frontend/src/features/auth/components/` | Empty directory |

### 3.6 Frontend — RESOLVE DUPLICATE

| Action | File | Decision |
|---|---|---|
| **AUDIT** | `src/data/historicalEvents.js` vs `features/practice/data/historicalEvents.js` | Both files contain identical content (2937 bytes each). Delete `src/data/historicalEvents.js`. Canonical location: `features/practice/data/historicalEvents.js`. Update any imports. |
| **DELETE DIR** | `frontend/src/data/` | Now empty after removing the duplicate |

### 3.7 Root — MOVE (docs)

| Action | From | To |
|---|---|---|
| **MOVE** | `tradelearn_architecture_audit.md` | `docs/architecture-audit-2026.md` |

### 3.8 .gitignore Updates

Add the following entries to `backend/.gitignore`:
```
# Runtime logs — never commit
logs/
*.log
```

Add the following entries to root `.gitignore`:
```
# IDE settings (if not already present)
.vscode/
```

---

## Section 4: Risks

### Risk Matrix

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Java `package` declaration mismatch after moving files | HIGH | CRITICAL — app won't compile | Move files using IDE refactoring tools (IntelliJ "Move" with refactor imports). Update package declarations manually if using file system moves. |
| Spring Boot fails to find beans after package move | MEDIUM | HIGH — app starts but features break | `@ComponentScan` in `ServerApplication.java` scans `com.tradelearn.server` — all moved packages remain under this root. No change needed to scan config. |
| Frontend imports break after deleting `src/data/` | LOW | MEDIUM | Only `historicalEvents.js` is in `src/data/`. Check all imports for this file first using `grep`. |
| Vercel deployment breaks | LOW | HIGH | `vercel.json` at repo root is what Vercel reads. `frontend/vercel.json` is for Vercel CLI local use. **Keep both.** |
| `backend/logs/` in git history | LOW | LOW | Files are committed — use `git rm -r --cached backend/logs/` to untrack without deleting. Add to `.gitignore`. |

### ⚠️ Non-Negotiable: Do NOT Move

| Item | Reason |
|---|---|
| `backend/.mvn/` | Maven wrapper config — must stay at Maven root |
| `backend/mvnw`, `backend/mvnw.cmd` | Maven wrapper scripts — must stay at Maven root |
| `backend/pom.xml` | Maven project definition — must stay at Maven root |
| `backend/src/main/resources/` | Spring Boot resource loading convention — must stay at `src/main/resources/` |
| `backend/src/main/resources/db/migration/` | Flyway convention — must stay under `resources/` |
| `backend/src/main/resources/candles/` | Classpath resource loading — `ClassPathResource` references |
| `frontend/public/` | Create React App convention |
| `frontend/package.json` | npm convention |
| `.github/workflows/` | GitHub Actions convention |

---

## Section 5: Step-by-Step Migration Plan

### Phase 0 — Preparation (1 hour)

1. [ ] Create a feature branch: `git checkout -b refactor/repository-structure`
2. [ ] Run the full test suite: `cd backend && ./mvnw test` — record baseline pass/fail
3. [ ] Take a note of currently working URLs in frontend

### Phase 1 — Backend: Delete Empty Directories (30 min)

> Risk: ZERO — these directories are verified empty.

1. [ ] Delete empty backend stubs:
   ```powershell
   # From: backend/src/main/java/com/tradelearn/server/
   Remove-Item -Recurse -Force controller, model, repository, service, security, socket, exception, middleware, util, validation, config
   ```
2. [ ] Verify no Java files were deleted (check git diff)
3. [ ] Untrack `backend/logs/` from git:
   ```bash
   git rm -r --cached backend/logs/
   ```
4. [ ] Update `backend/.gitignore` to add `logs/` and `*.log`
5. [ ] Run `./mvnw compile` — must still succeed

### Phase 2 — Backend: Flatten `common/` (45 min)

> Risk: LOW — updating package declarations in 2 files + any imports.

1. [ ] Move `WebConfig.java`:
   - Change `package com.tradelearn.server.common.config;` → `package com.tradelearn.server.common;`
   - Move file to `common/WebConfig.java`
   - Delete `common/config/` directory
2. [ ] Move `HealthController.java`:
   - Change `package com.tradelearn.server.common.controller;` → `package com.tradelearn.server.common;`
   - Move file to `common/HealthController.java`
   - Delete `common/controller/` directory
3. [ ] Search entire backend for any `import com.tradelearn.server.common.config.WebConfig` or `import com.tradelearn.server.common.controller.HealthController` and update
4. [ ] Run `./mvnw compile` — must succeed

### Phase 3 — Backend: Merge `infrastructure/pipeline/` (20 min)

> Risk: LOW — one file move within infrastructure.

1. [ ] Move `GameMetricsService.java`:
   - Change `package com.tradelearn.server.infrastructure.pipeline;` → `package com.tradelearn.server.infrastructure.scheduling;`
   - Move file to `infrastructure/scheduling/GameMetricsService.java`
   - Delete `infrastructure/pipeline/` directory
2. [ ] Search for any `import com.tradelearn.server.infrastructure.pipeline.GameMetricsService` and update
3. [ ] Run `./mvnw compile` — must succeed

### Phase 4 — Backend: Move Docs (10 min)

1. [ ] Move `backend/PRODUCTION_ARCHITECTURE.md` → `docs/PRODUCTION_ARCHITECTURE.md`
2. [ ] Update `docs/` index or README if applicable

### Phase 5 — Frontend: Delete Dead Artifacts (20 min)

1. [ ] Delete `frontend/fix_imports.js`
2. [ ] Delete `frontend/logo.svg` (verify not referenced: `grep -r "logo.svg" frontend/src/`)
3. [ ] Delete `frontend/src/utils/` (verify empty: `ls frontend/src/utils/`)
4. [ ] Delete `frontend/src/features/simulator/data/` (verify empty)
5. [ ] Delete `frontend/src/features/social/pages/` (verify empty)
6. [ ] Delete `frontend/src/features/auth/components/` (verify empty)

### Phase 6 — Frontend: Resolve Duplicate `historicalEvents.js` (20 min)

1. [ ] Verify both files are identical:
   ```bash
   diff frontend/src/data/historicalEvents.js frontend/src/features/practice/data/historicalEvents.js
   ```
2. [ ] Search for all imports of the root-level copy:
   ```bash
   grep -r "from.*src/data/historicalEvents" frontend/src/
   grep -r "from.*'../../data/historicalEvents" frontend/src/
   grep -r "from.*'../data/historicalEvents" frontend/src/
   ```
3. [ ] Update any found imports to point to `features/practice/data/historicalEvents.js`
4. [ ] Delete `frontend/src/data/historicalEvents.js`
5. [ ] Delete `frontend/src/data/` directory (now empty)
6. [ ] Run `npm start` in frontend — verify no import errors

### Phase 7 — Root: Move Audit Doc (5 min)

1. [ ] Move `tradelearn_architecture_audit.md` → `docs/architecture-audit-2026.md`
2. [ ] Update `README.md` if it links to this file

### Phase 8 — Generate Documentation (30 min)

1. [ ] Finalize `ARCHITECTURE.md` (already created)
2. [ ] Finalize `REFACTOR_PLAN.md` (this document)
3. [ ] Finalize `BEFORE_AFTER_TREE.md`
4. [ ] Update `docs/architecture.md` to reference `ARCHITECTURE.md`

### Phase 9 — Verification (30 min)

1. [ ] `cd backend && ./mvnw test` — all tests must pass
2. [ ] `cd frontend && npm start` — no console errors
3. [ ] `cd frontend && npm run build` — successful production build
4. [ ] Manually navigate every route in the app
5. [ ] Test WebSocket connection (enter a game)

### Phase 10 — Commit & PR (15 min)

```bash
git add -A
git commit -m "refactor: clean repository structure — remove empty stubs, delete dead migration scripts, flatten common/ package, merge infrastructure/pipeline/ into scheduling/, resolve duplicate historicalEvents.js, move docs to docs/"
git push origin refactor/repository-structure
```

---

## Section 6: Open-Source Readiness Checklist

These items should be addressed before open-source release:

| Item | Status | Action |
|---|---|---|
| `backend/logs/` committed | ❌ | Remove from git history + add to `.gitignore` |
| `fix_imports.js` at frontend root | ❌ | Delete — migration artifact |
| `tradelearn_architecture_audit.md` at root | ❌ | Move to `docs/` |
| `.env` committed in `frontend/` | ⚠️ | Check contents — must not contain secrets; `REACT_APP_API_URL` only |
| `.vscode/` settings committed | ⚠️ | Add to `.gitignore` or ensure no personal settings |
| `backend/PRODUCTION_ARCHITECTURE.md` inside backend | ❌ | Move to `docs/` |
| Demo seed SQL (`seed-demo.sql`) | ✅ | Appropriate for OSS — good for contributors |
| `docs/demo-accounts.md` | ✅ | Good contributor resource |
| `CONTRIBUTING.md` | ✅ | Good |
| Test coverage | ❌ | Only 6 test files — need more coverage before OSS |
| `README.md` | ✅ | 12KB — comprehensive |
| License file | ❌ | No LICENSE file found — required for open-source |

---

## Section 7: Senior Engineer Review Verdict

### What Would Impress a Reviewing Engineer

- ✅ Feature-based architecture is **already in place** on the backend (game/, matchmaking/, auth/, etc.)
- ✅ Infrastructure isolation is **already good** (`infrastructure/` package with redis/, resilience/, scheduling/)
- ✅ WebSocket architecture is well-designed (STOMP + Redis relay for horizontal scaling)
- ✅ Resilience patterns are sophisticated (circuit breaker, crash recovery, graceful degradation)
- ✅ Redis usage is idiomatic (ZSET for matchmaking, Lua for atomic rematch consent)
- ✅ Frontend feature-slicing is already done

### What Would Concern a Reviewing Engineer

- ❌ **5+ empty top-level packages** — immediate red flag suggesting abandoned refactoring attempt
- ❌ **`socket/` empty directory next to `websocket/` active directory** — naming inconsistency
- ❌ **`fix_imports.js` in the repo** — shows migration happened messily
- ❌ **`tradelearn_architecture_audit.md` at root** — audit doc should be in `docs/`, not polluting root
- ❌ **Runtime logs committed** — junior developer mistake
- ❌ **Only 6 test files** for a 27-package backend — very low coverage
- ❌ **No LICENSE file** — cannot be properly open-sourced
- ❌ **`PRODUCTION_ARCHITECTURE.md` inside `backend/`** — wrong location
