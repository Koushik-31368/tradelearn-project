-- =============================================================
-- TradeLearn Demo Seed Data
-- =============================================================
-- Creates 5 demo accounts with realistic match history and
-- leaderboard standings. Designed for portfolio demos.
--
-- USAGE:
--   MySQL:      mysql -u root -p tradelearn < seed-demo.sql
--   PostgreSQL: psql -U postgres -d tradelearn -f seed-demo.sql
--
-- LOGIN CREDENTIALS (all passwords = "Demo1234"):
--   demo.diamond@tradelearn.com  — Diamond tier (top player)
--   demo.gold@tradelearn.com     — Gold tier (experienced)
--   demo.silver@tradelearn.com   — Silver tier (intermediate)
--   demo.bronze@tradelearn.com   — Bronze tier (beginner)
--   demo.rookie@tradelearn.com   — Bronze tier (new player)
--
-- BCrypt hash of "Demo1234" (cost=10):
--   $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhu2
-- =============================================================

-- ─────────────────────────────────────────────────────────────
-- 1. DEMO USERS
-- ─────────────────────────────────────────────────────────────
-- Skip insert if email already exists (safe to re-run)
INSERT INTO users (username, email, password, rating, wins, losses, win_streak, xp, login_streak)
SELECT 'ArjunMehra',    'demo.diamond@tradelearn.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhu2', 1820, 38, 11, 4, 4800, 12
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo.diamond@tradelearn.com');

INSERT INTO users (username, email, password, rating, wins, losses, win_streak, xp, login_streak)
SELECT 'PriyaNair',     'demo.gold@tradelearn.com',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhu2', 1245, 22, 18, 1, 2600, 7
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo.gold@tradelearn.com');

INSERT INTO users (username, email, password, rating, wins, losses, win_streak, xp, login_streak)
SELECT 'RohanSingh',    'demo.silver@tradelearn.com',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhu2',  820, 14, 19, 0, 1400, 3
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo.silver@tradelearn.com');

INSERT INTO users (username, email, password, rating, wins, losses, win_streak, xp, login_streak)
SELECT 'DevikaRao',     'demo.bronze@tradelearn.com',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhu2',  430,  5, 14, 0,  550, 2
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo.bronze@tradelearn.com');

INSERT INTO users (username, email, password, rating, wins, losses, win_streak, xp, login_streak)
SELECT 'KarthikV',      'demo.rookie@tradelearn.com',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhu2',  210,  1,  5, 0,  150, 1
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'demo.rookie@tradelearn.com');

-- ─────────────────────────────────────────────────────────────
-- 2. FINISHED GAMES (ArjunMehra = creator in most)
--    Creator = ArjunMehra (id resolved below via subquery)
-- ─────────────────────────────────────────────────────────────

-- ── Helper: resolve user IDs ──────────────────────────────────
-- All game inserts use subqueries so this file works across
-- fresh DBs with any auto-increment starting point.

-- GAME 1: ArjunMehra (WIN) vs PriyaNair — INFY
INSERT INTO games (stock_symbol, duration_minutes, status, creator_id, opponent_id, winner_id,
                   starting_balance, creator_final_balance, opponent_final_balance,
                   creator_rating_delta, opponent_rating_delta, start_time, end_time)
SELECT 'INFY', 5, 'FINISHED',
       (SELECT id FROM users WHERE email = 'demo.diamond@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.gold@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.diamond@tradelearn.com'),
       1000000, 1148200, 952000, 18, -18,
       NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY + INTERVAL 5 MINUTE;

-- GAME 2: PriyaNair (WIN) vs ArjunMehra — TCS (upset)
INSERT INTO games (stock_symbol, duration_minutes, status, creator_id, opponent_id, winner_id,
                   starting_balance, creator_final_balance, opponent_final_balance,
                   creator_rating_delta, opponent_rating_delta, start_time, end_time)
SELECT 'TCS', 5, 'FINISHED',
       (SELECT id FROM users WHERE email = 'demo.gold@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.diamond@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.gold@tradelearn.com'),
       1000000, 1072500, 940000, 22, -22,
       NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY + INTERVAL 5 MINUTE;

-- GAME 3: ArjunMehra (WIN) vs RohanSingh — RELIANCE
INSERT INTO games (stock_symbol, duration_minutes, status, creator_id, opponent_id, winner_id,
                   starting_balance, creator_final_balance, opponent_final_balance,
                   creator_rating_delta, opponent_rating_delta, start_time, end_time)
SELECT 'RELIANCE', 5, 'FINISHED',
       (SELECT id FROM users WHERE email = 'demo.diamond@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.silver@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.diamond@tradelearn.com'),
       1000000, 1210000, 870000, 14, -14,
       NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY + INTERVAL 5 MINUTE;

-- GAME 4: RohanSingh (WIN) vs DevikaRao — HDFCBANK
INSERT INTO games (stock_symbol, duration_minutes, status, creator_id, opponent_id, winner_id,
                   starting_balance, creator_final_balance, opponent_final_balance,
                   creator_rating_delta, opponent_rating_delta, start_time, end_time)
SELECT 'HDFCBANK', 5, 'FINISHED',
       (SELECT id FROM users WHERE email = 'demo.silver@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.bronze@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.silver@tradelearn.com'),
       1000000, 1085000, 921000, 16, -16,
       NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY + INTERVAL 5 MINUTE;

-- GAME 5: ArjunMehra (WIN) vs DevikaRao — WIPRO
INSERT INTO games (stock_symbol, duration_minutes, status, creator_id, opponent_id, winner_id,
                   starting_balance, creator_final_balance, opponent_final_balance,
                   creator_rating_delta, opponent_rating_delta, start_time, end_time)
SELECT 'WIPRO', 5, 'FINISHED',
       (SELECT id FROM users WHERE email = 'demo.diamond@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.bronze@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.diamond@tradelearn.com'),
       1000000, 1195000, 905000, 11, -11,
       NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY + INTERVAL 5 MINUTE;

-- GAME 6: DRAW — ArjunMehra vs PriyaNair (both close to even)
INSERT INTO games (stock_symbol, duration_minutes, status, creator_id, opponent_id, winner_id,
                   starting_balance, creator_final_balance, opponent_final_balance,
                   creator_rating_delta, opponent_rating_delta, start_time, end_time)
SELECT 'SBIN', 5, 'FINISHED',
       (SELECT id FROM users WHERE email = 'demo.diamond@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.gold@tradelearn.com'),
       NULL,
       1000000, 1002000, 998500, 0, 0,
       NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 6 DAY + INTERVAL 5 MINUTE;

-- GAME 7: PriyaNair (WIN) vs RohanSingh — ITC
INSERT INTO games (stock_symbol, duration_minutes, status, creator_id, opponent_id, winner_id,
                   starting_balance, creator_final_balance, opponent_final_balance,
                   creator_rating_delta, opponent_rating_delta, start_time, end_time)
SELECT 'ITC', 5, 'FINISHED',
       (SELECT id FROM users WHERE email = 'demo.gold@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.silver@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.gold@tradelearn.com'),
       1000000, 1125000, 913000, 15, -15,
       NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 7 DAY + INTERVAL 5 MINUTE;

-- GAME 8: KarthikV (WIN) vs DevikaRao — beginner match
INSERT INTO games (stock_symbol, duration_minutes, status, creator_id, opponent_id, winner_id,
                   starting_balance, creator_final_balance, opponent_final_balance,
                   creator_rating_delta, opponent_rating_delta, start_time, end_time)
SELECT 'MARUTI', 5, 'FINISHED',
       (SELECT id FROM users WHERE email = 'demo.rookie@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.bronze@tradelearn.com'),
       (SELECT id FROM users WHERE email = 'demo.rookie@tradelearn.com'),
       1000000, 1042000, 975000, 20, -20,
       NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY + INTERVAL 5 MINUTE;

-- ─────────────────────────────────────────────────────────────
-- 3. MATCH STATS (one row per player per game)
--    game_id resolved via subquery on (stock_symbol, creator_id, end_time window)
-- ─────────────────────────────────────────────────────────────

-- Game 1 stats: ArjunMehra
INSERT INTO match_stats (game_id, user_id, peak_equity, max_drawdown, total_trades, profitable_trades, final_equity, final_score)
SELECT
    (SELECT id FROM games WHERE stock_symbol='INFY' AND creator_id=(SELECT id FROM users WHERE email='demo.diamond@tradelearn.com') ORDER BY id DESC LIMIT 1),
    (SELECT id FROM users WHERE email='demo.diamond@tradelearn.com'),
    1215000, 3.2, 8, 6, 1148200, 82.4;

-- Game 1 stats: PriyaNair
INSERT INTO match_stats (game_id, user_id, peak_equity, max_drawdown, total_trades, profitable_trades, final_equity, final_score)
SELECT
    (SELECT id FROM games WHERE stock_symbol='INFY' AND creator_id=(SELECT id FROM users WHERE email='demo.diamond@tradelearn.com') ORDER BY id DESC LIMIT 1),
    (SELECT id FROM users WHERE email='demo.gold@tradelearn.com'),
    1010000, 8.1, 12, 5, 952000, 48.3;

-- Game 3 stats: ArjunMehra
INSERT INTO match_stats (game_id, user_id, peak_equity, max_drawdown, total_trades, profitable_trades, final_equity, final_score)
SELECT
    (SELECT id FROM games WHERE stock_symbol='RELIANCE' AND creator_id=(SELECT id FROM users WHERE email='demo.diamond@tradelearn.com') ORDER BY id DESC LIMIT 1),
    (SELECT id FROM users WHERE email='demo.diamond@tradelearn.com'),
    1260000, 4.1, 6, 5, 1210000, 88.1;

-- Game 3 stats: RohanSingh
INSERT INTO match_stats (game_id, user_id, peak_equity, max_drawdown, total_trades, profitable_trades, final_equity, final_score)
SELECT
    (SELECT id FROM games WHERE stock_symbol='RELIANCE' AND creator_id=(SELECT id FROM users WHERE email='demo.diamond@tradelearn.com') ORDER BY id DESC LIMIT 1),
    (SELECT id FROM users WHERE email='demo.silver@tradelearn.com'),
    1050000, 14.5, 18, 7, 870000, 35.2;

-- Game 4 stats: RohanSingh
INSERT INTO match_stats (game_id, user_id, peak_equity, max_drawdown, total_trades, profitable_trades, final_equity, final_score)
SELECT
    (SELECT id FROM games WHERE stock_symbol='HDFCBANK' AND creator_id=(SELECT id FROM users WHERE email='demo.silver@tradelearn.com') ORDER BY id DESC LIMIT 1),
    (SELECT id FROM users WHERE email='demo.silver@tradelearn.com'),
    1120000, 5.8, 9, 6, 1085000, 71.5;

-- Game 4 stats: DevikaRao
INSERT INTO match_stats (game_id, user_id, peak_equity, max_drawdown, total_trades, profitable_trades, final_equity, final_score)
SELECT
    (SELECT id FROM games WHERE stock_symbol='HDFCBANK' AND creator_id=(SELECT id FROM users WHERE email='demo.silver@tradelearn.com') ORDER BY id DESC LIMIT 1),
    (SELECT id FROM users WHERE email='demo.bronze@tradelearn.com'),
    1005000, 12.2, 15, 5, 921000, 38.8;

-- ─────────────────────────────────────────────────────────────
-- 4. VERIFY (optional — comment out for production use)
-- ─────────────────────────────────────────────────────────────
-- SELECT id, username, email, rating FROM users WHERE email LIKE 'demo.%@tradelearn.com' ORDER BY rating DESC;
-- SELECT COUNT(*) AS total_games FROM games WHERE status = 'FINISHED';
-- SELECT COUNT(*) AS total_stats FROM match_stats;
