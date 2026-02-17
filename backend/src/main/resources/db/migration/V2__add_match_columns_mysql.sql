-- ============================================================
-- V2 (MySQL): Add 1v1 match support columns
-- Run this on local MySQL (tradelearn_db)
-- ============================================================

-- GAMES table: add match-specific columns
ALTER TABLE games ADD COLUMN winner_id BIGINT NULL;
ALTER TABLE games ADD COLUMN starting_balance DOUBLE DEFAULT 1000000.0;
ALTER TABLE games ADD COLUMN start_time DATETIME NULL;
ALTER TABLE games ADD COLUMN end_time DATETIME NULL;
ALTER TABLE games ADD COLUMN creator_final_balance DOUBLE NULL;
ALTER TABLE games ADD COLUMN opponent_final_balance DOUBLE NULL;

ALTER TABLE games
    ADD CONSTRAINT fk_games_winner
    FOREIGN KEY (winner_id) REFERENCES users(id);

-- TRADES table: add game_id to link trades to a match
ALTER TABLE trades ADD COLUMN game_id BIGINT NULL;

CREATE INDEX idx_trades_game_id ON trades(game_id);
