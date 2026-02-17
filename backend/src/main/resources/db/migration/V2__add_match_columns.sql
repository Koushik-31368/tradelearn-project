-- ============================================================
-- V2: Add 1v1 match support columns
-- Run against PostgreSQL (Render) and MySQL (local)
-- ============================================================

-- GAMES table: add match-specific columns
ALTER TABLE games ADD COLUMN IF NOT EXISTS winner_id BIGINT;
ALTER TABLE games ADD COLUMN IF NOT EXISTS starting_balance DOUBLE PRECISION DEFAULT 1000000.0;
ALTER TABLE games ADD COLUMN IF NOT EXISTS start_time TIMESTAMP;
ALTER TABLE games ADD COLUMN IF NOT EXISTS end_time TIMESTAMP;
ALTER TABLE games ADD COLUMN IF NOT EXISTS creator_final_balance DOUBLE PRECISION;
ALTER TABLE games ADD COLUMN IF NOT EXISTS opponent_final_balance DOUBLE PRECISION;

-- Foreign key for winner → users
ALTER TABLE games
    ADD CONSTRAINT fk_games_winner
    FOREIGN KEY (winner_id) REFERENCES users(id);

-- TRADES table: add game_id to link trades to a match
ALTER TABLE trades ADD COLUMN IF NOT EXISTS game_id BIGINT;

-- Index for quick lookup of trades by game
CREATE INDEX IF NOT EXISTS idx_trades_game_id ON trades(game_id);
