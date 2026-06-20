# Screenshot & GIF Capture Guide

This guide helps you capture the demo assets that go into the README.

---

## Setup Before Capturing

1. Load demo seed data: `mysql -u root -p tradelearn < backend/src/main/resources/db/seed-demo.sql`
2. Start the app locally: `docker-compose up`
3. Open Chrome at 1440×900 window size
4. Log in as `demo.diamond@tradelearn.com` / `Demo1234`

---

## Screenshots (save to `docs/screenshots/`)

| Filename | URL | Notes |
|---|---|---|
| `home.png` | `/` | Logged out, hero + feature cards visible |
| `match.png` | `/game/{id}` | Active match, both scores + chart + order panel |
| `leaderboard.png` | `/leaderboard` | ArjunMehra at #1 with Diamond badge |
| `simulator.png` | `/simulator` | Chart + SMA + sentiment panel + portfolio |
| `practice.png` | `/practice` | Historical event selected, replay in progress |
| `profile.png` | `/profile` | ArjunMehra: 1820 ELO, 38W/11L, recent matches |

**How to take pixel-perfect screenshots in Chrome:**
1. Open DevTools (F12)
2. Press Cmd/Ctrl+Shift+P
3. Type "Capture full size screenshot" → Enter

---

## GIFs (save to `docs/screenshots/`)

| Filename | Duration | Content |
|---|---|---|
| `demo-match.gif` | 20–25s | Buy order → candles advance → sell → P&L updates |
| `demo-simulator.gif` | 15s | Chart browsing → place order → portfolio updates |
| `demo-matchmaking.gif` | 10s | Queue → "match found" → game screen loads |

**Recommended tools:**
- Windows: [ScreenToGif](https://www.screentogif.com/) (free, open source)
- Mac: [LICEcap](https://www.cockos.com/licecap/) or Kap
- Cross-platform: [Gyroflow Toolbox](https://gyroflowtoolbox.io/)

**Settings:** 800×500 region · 15 fps · optimize output (<3MB per GIF)

---

## After Capturing

1. Copy all files to `docs/screenshots/`
2. README already references them — they will auto-render on GitHub
3. Commit: `git add docs/screenshots/ && git commit -m "docs: add demo screenshots and GIFs"`
