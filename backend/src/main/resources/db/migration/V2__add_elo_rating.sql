-- Add ELO rating column to users table (default 1000)
ALTER TABLE users ADD COLUMN rating INT NOT NULL DEFAULT 1000;

-- Add ELO rating change columns to games table
ALTER TABLE games ADD COLUMN creator_rating_delta INT DEFAULT NULL;
ALTER TABLE games ADD COLUMN opponent_rating_delta INT DEFAULT NULL;
