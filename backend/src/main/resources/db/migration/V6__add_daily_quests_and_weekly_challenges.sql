CREATE TABLE daily_quests (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    quest_type VARCHAR(50) NOT NULL, -- e.g., 'LOGIN', 'LESSON', 'QUIZ', 'SIMULATOR_TRADE', 'CHALLENGE_FRIEND'
    target_value INT NOT NULL DEFAULT 1,
    xp_reward INT NOT NULL DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_daily_quests (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quest_id BIGINT NOT NULL REFERENCES daily_quests(id) ON DELETE CASCADE,
    progress INT NOT NULL DEFAULT 0,
    completed BOOLEAN DEFAULT FALSE,
    quest_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, quest_id, quest_date)
);

CREATE TABLE weekly_challenges (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    challenge_type VARCHAR(50) NOT NULL, -- e.g., 'EARN_XP', 'WIN_MATCHES'
    target_value INT NOT NULL DEFAULT 1,
    xp_reward INT NOT NULL DEFAULT 0,
    bonus_points INT DEFAULT 0,
    badge_reward VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_weekly_challenges (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenge_id BIGINT NOT NULL REFERENCES weekly_challenges(id) ON DELETE CASCADE,
    progress INT NOT NULL DEFAULT 0,
    completed BOOLEAN DEFAULT FALSE,
    week_start_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, challenge_id, week_start_date)
);

-- Insert initial Daily Quests
INSERT INTO daily_quests (name, description, quest_type, target_value, xp_reward) VALUES 
('Daily Login', 'Log into TradeLearn today.', 'LOGIN', 1, 10),
('Learn the Ropes', 'Complete 1 learning lesson.', 'LESSON', 1, 20),
('Test Your Knowledge', 'Complete 1 quiz.', 'QUIZ', 1, 20),
('Practice Makes Perfect', 'Make 3 trades in the Simulator.', 'SIMULATOR_TRADE', 3, 30),
('Friendly Competition', 'Challenge a friend to a match.', 'CHALLENGE_FRIEND', 1, 40);

-- Insert initial Weekly Challenges
INSERT INTO weekly_challenges (name, description, challenge_type, target_value, xp_reward, badge_reward) VALUES
('XP Hunter', 'Earn 500 XP this week.', 'EARN_XP', 500, 100, 'Dedicated Scholar'),
('Match Winner', 'Win 5 competitive matches.', 'WIN_MATCHES', 5, 100, 'Fierce Competitor');
