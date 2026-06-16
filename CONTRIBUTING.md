# Contributing to TradeLearn

Thank you for your interest in contributing! This document explains how to set up the project for development and how to submit changes.

---

## Table of Contents

- [Local Development Setup](#local-development-setup)
- [Branch Naming](#branch-naming)
- [Commit Convention](#commit-convention)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Testing](#testing)

---

## Local Development Setup

### 1. Fork and clone

```bash
git clone https://github.com/<your-username>/tradelearn.git
cd tradelearn
```

### 2. Set up the backend

```bash
cd backend

# Create local config (gitignored — never committed)
cp .env.example .env
# Edit application-local.properties with your DB credentials and JWT secret
```

Start a local Redis instance:
```bash
# macOS
brew install redis && brew services start redis

# Windows (WSL or Docker)
docker run -d -p 6379:6379 redis:7-alpine
```

Start the backend:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Set up the frontend

```bash
cd frontend
cp .env.example .env   # already points to localhost:8080
npm install
npm start
```

### 4. Verify everything works

- Backend health: `http://localhost:8080/actuator/health` → `{"status":"UP"}`
- Frontend: `http://localhost:3000`
- Register an account, create a game, and verify the WebSocket connection establishes.

---

## Branch Naming

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/<short-description>` | `feature/tournament-mode` |
| Bug fix | `fix/<issue-or-description>` | `fix/elo-calculation-tie` |
| Refactor | `refactor/<description>` | `refactor/extract-trade-panel` |
| Docs | `docs/<description>` | `docs/api-reference` |
| Hotfix | `hotfix/<description>` | `hotfix/prod-db-timeout` |

Always branch off `main`.

---

## Commit Convention

Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>(<scope>): <short summary>

[optional body]
[optional footer]
```

**Types:** `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`

**Examples:**
```
feat(multiplayer): add rematch consent via Redis atomic Lua script
fix(simulator): deduplicate candle history on reconnect
docs(readme): add Docker quick-start and env variable table
test(elo): add edge cases for K-factor calculation
```

---

## Pull Request Process

1. **Ensure CI passes** — `./mvnw verify` (backend) and `npm test -- --watchAll=false` (frontend) must be green.
2. **Keep PRs small** — one concern per PR. Large PRs are hard to review.
3. **Write a clear description** — what changed, why, and how to test it.
4. **Reference issues** — use `Closes #<issue-number>` in the PR body.
5. **Request a review** — tag at least one maintainer.

PRs are merged with **Squash and Merge** to keep `main` history clean.

---

## Coding Standards

### Backend (Java)

- **Java 21** — use modern features (records, text blocks, switch expressions) where appropriate.
- **Package structure** — follow the existing `controller / service / repository / dto / model` layering. Do not skip layers (e.g., controllers should not access repositories directly).
- **Logging** — use SLF4J (`LoggerFactory.getLogger(ClassName.class)`), never `System.out.println`. Log at `DEBUG` in service methods, `INFO` for lifecycle events, `WARN` for degraded state, `ERROR` for failures.
- **Transactions** — all DB mutations must be in `@Transactional` methods. Side effects (Redis, WebSocket) go in `afterCommit()` hooks — never inside the transaction boundary.
- **Null safety** — avoid `@SuppressWarnings("null")`. Use `Optional`, null checks, or `Objects.requireNonNull` instead.
- **No magic strings** — use constants or enums for status values (`"WAITING"`, `"ACTIVE"`, `"FINISHED"`, `"ABANDONED"`).

### Frontend (React)

- **Components** — functional components with hooks only. No class components.
- **HTTP requests** — always use the shared `api` Axios instance from `utils/api.js`. Never use bare `fetch()`.
- **Auth** — use `useAuth()` from `AuthContext` for current user and token. Never read `localStorage` directly in components.
- **CSS** — per-component CSS files co-located with the component. Use design tokens from `styles/theme.css` (CSS custom properties). No inline `style={{ ... }}` for colors/spacing.
- **No IIFE in JSX** — extract complex render logic to named sub-components or variables defined above the return statement.

---

## Testing

### Backend

Run all tests:
```bash
cd backend
./mvnw test
```

New code should have unit tests for:
- Pure utility functions (`EloUtil`, `ScoringUtil`)
- Service layer logic that doesn't require external dependencies
- Integration tests for critical REST endpoints (use `@SpringBootTest` + `MockMvc`)

### Frontend

Run all tests:
```bash
cd frontend
npm test -- --watchAll=false
```

New components should have at least a smoke test (renders without crashing).
