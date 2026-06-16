CREATE TABLE user_lesson_progress (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lesson_id VARCHAR(100) NOT NULL,
    completed BOOLEAN DEFAULT TRUE,
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, lesson_id)
);

CREATE TABLE achievements (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    icon VARCHAR(20) NOT NULL,
    condition_type VARCHAR(50) NOT NULL,
    condition_value INT NOT NULL,
    xp_reward INT NOT NULL
);

CREATE TABLE user_achievements (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_id BIGINT NOT NULL REFERENCES achievements(id) ON DELETE CASCADE,
    earned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, achievement_id)
);

-- Seed Achievements
INSERT INTO achievements (name, description, icon, condition_type, condition_value, xp_reward) VALUES
('First Steps', 'Complete your very first lesson', '📚', 'LESSON_COUNT', 1, 25),
('Trading Basics', 'Complete the entire Trading Basics path', '🎓', 'PATH_COMPLETE_BASICS', 1, 50),
('First Trade', 'Play your first trading game', '📈', 'GAMES_PLAYED', 1, 25),
('First Win', 'Win your first multiplayer match', '🏆', 'GAMES_WON', 1, 50),
('7 Day Streak', 'Log in for 7 consecutive days', '🔥', 'STREAK', 7, 100),
('100 XP', 'Earn your first 100 XP', '✨', 'XP_REACHED', 100, 25),
('Golden Trader', 'Reach the Gold tier (800+ ELO)', '🏅', 'TIER_GOLD', 1, 100),
('Diamond Hands', 'Reach the Diamond tier (1500+ ELO)', '💎', 'TIER_DIAMOND', 1, 200);
