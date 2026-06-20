# Demo Accounts

Pre-seeded demo accounts for portfolio demonstrations. All passwords are `Demo1234`.

> **Load seed data first:**
> ```bash
> # MySQL
> mysql -u root -p tradelearn < backend/src/main/resources/db/seed-demo.sql
>
> # PostgreSQL
> psql -U postgres -d tradelearn -f backend/src/main/resources/db/seed-demo.sql
> ```

---

## Accounts

| Username | Email | Password | ELO | Tier | Best For |
|---|---|---|---|---|---|
| **ArjunMehra** | `demo.diamond@tradelearn.com` | `Demo1234` | 1820 | 💎 Diamond | Full profile showcase, leaderboard top-3 |
| **PriyaNair** | `demo.gold@tradelearn.com` | `Demo1234` | 1245 | 🥇 Gold | Mid-tier competitive play demo |
| **RohanSingh** | `demo.silver@tradelearn.com` | `Demo1234` | 820 | 🥈 Silver | Improvement arc / underdog story |
| **DevikaRao** | `demo.bronze@tradelearn.com` | `Demo1234` | 430 | 🥉 Bronze | New player onboarding demo |
| **KarthikV** | `demo.rookie@tradelearn.com` | `Demo1234` | 210 | 🥉 Bronze | Fresh account, first win |

---

## Suggested Demo Flow

### For a 2-minute recruiter demo:
1. Log in as **ArjunMehra** (Diamond)
2. Show the **Profile page** — ELO badge, W/L record, recent match history
3. Navigate to **Leaderboard** — ArjunMehra appears in top 3 with Diamond badge
4. Open **Multiplayer Lobby** — create a match on INFY / 5 min
5. In a second browser tab, log in as **PriyaNair** (Gold) and join the match
6. Show both players trading simultaneously in real-time
7. Let the match finish — show the **Match Result** screen with ELO changes

### For a simulator demo:
1. Log in as any account
2. Navigate to **Simulator** — show the live candlestick chart and order panel
3. Place a BUY order — show portfolio updating in real time
4. Switch to **Practice Mode** — replay the 2020 COVID crash event

### For a backtest demo:
1. Navigate to **Strategies** — select SMA Crossover
2. Open **Backtest** — run against INFY data (2023–2024)
3. Show equity curve, max drawdown %, win rate

---

## Notes

- All demo accounts use BCrypt-hashed passwords (cost=10)
- Match history shows realistic profit/loss percentages (+14.8% to -8.0%)
- ELO deltas reflect actual match outcomes (±11 to ±22 points)
- Seed file is idempotent — safe to run multiple times
