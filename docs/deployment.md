# TradeLearn Deployment Guide

## Option 1 — Docker Compose (Local Full Stack)

### Prerequisites
- Docker Desktop installed and running
- `.env` file at the project root (see `backend/.env.example`)

### Steps

```bash
# 1. Create the root .env file
cp backend/.env.example .env
# Edit .env — fill in DATABASE_URL, DATABASE_PASSWORD, JWT_SECRET

# 2. Start all services
docker-compose up --build

# Services:
#   Redis:    localhost:6379
#   Backend:  localhost:8080
#   Frontend: localhost:3000
```

To stop:
```bash
docker-compose down
# To also remove the Redis volume:
docker-compose down -v
```

---

## Option 2 — Render (Production Backend)

### Database

1. Create a **PostgreSQL** instance on Render.
2. Copy the **Internal Database URL** — it will be your `DATABASE_URL`.

### Redis

1. Create a **Redis** instance on Render (or use Upstash for free tier).
2. Copy the Redis hostname and port.

### Backend Web Service

1. Create a **Web Service** on Render.
2. Set the following:
   - **Runtime:** Docker
   - **Dockerfile path:** `./backend/Dockerfile`
   - **Build context:** `./backend`

3. Add **Environment Variables** in the Render dashboard:

| Key | Value |
|-----|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | `jdbc:postgresql://<render-host>/tradelearn_db?sslmode=require` |
| `DATABASE_USERNAME` | `<render-db-user>` |
| `DATABASE_PASSWORD` | `<render-db-password>` |
| `JWT_SECRET` | `<64-char-random-string>` |
| `REDIS_HOST` | `<render-redis-host>` |
| `REDIS_PORT` | `6379` |
| `CORS_ALLOWED_ORIGINS` | `https://your-app.vercel.app` |

4. Set **Health Check Path** to `/actuator/health`

### Generating a JWT secret

```bash
# macOS/Linux
openssl rand -hex 64

# Windows PowerShell
-join ((65..90) + (97..122) + (48..57) | Get-Random -Count 64 | % {[char]$_})
```

---

## Option 3 — Vercel (Frontend)

1. Import the repository into Vercel.
2. Set **Root Directory** to `frontend`.
3. Set **Build Command** to `npm run build`.
4. Set **Output Directory** to `build`.
5. Add **Environment Variable**:
   - `REACT_APP_API_URL` = `https://your-backend.onrender.com`

---

## CI/CD — GitHub Actions

The `.github/workflows/build.yml` workflow:
- **On every PR** to `main`: builds backend (Maven) and frontend (npm)
- **On push to `main`**: additionally builds and pushes Docker images to Docker Hub

Required GitHub Secrets:

| Secret | Description |
|--------|-------------|
| `DOCKERHUB_USERNAME` | Docker Hub username |
| `DOCKERHUB_TOKEN` | Docker Hub access token |
| `REACT_APP_API_URL` | Backend URL for frontend Docker build |

---

## Health Checks

| Endpoint | Expected response |
|---------|------------------|
| `GET /actuator/health` | `{"status":"UP"}` |
| `GET /actuator/info` | Build info |
| `GET /actuator/prometheus` | Prometheus metrics |

---

## Database Migrations

The application currently uses `spring.jpa.hibernate.ddl-auto=update` — Hibernate auto-creates/modifies tables on startup.

The SQL files in `backend/src/main/resources/db/migration/` are **reference scripts** documenting the schema evolution history. They are not executed automatically (Flyway is not configured).

If you need to migrate an existing database manually:
```bash
# Connect to your PostgreSQL instance
psql -h <host> -U <user> -d <dbname>
# Run scripts in order:
\i V2__add_match_columns.sql
\i V2_1__add_elo_rating.sql
\i V3__add_xp_and_streak.sql
# etc.
```
