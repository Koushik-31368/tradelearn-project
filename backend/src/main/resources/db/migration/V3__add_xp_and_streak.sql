ALTER TABLE users
ADD COLUMN xp INT DEFAULT 0,
ADD COLUMN login_streak INT DEFAULT 0,
ADD COLUMN longest_login_streak INT DEFAULT 0,
ADD COLUMN last_login_date DATE;
