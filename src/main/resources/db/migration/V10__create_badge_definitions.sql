CREATE TABLE badge_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    badge_type VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    icon_name VARCHAR(100),
    icon_url VARCHAR(255),
    rule_type VARCHAR(50) NOT NULL,
    rule_value DOUBLE PRECISION NOT NULL
);

-- Seed initial 6 badges
INSERT INTO badge_definitions (id, badge_type, title, description, icon_name, icon_url, rule_type, rule_value) VALUES
('a0000000-0000-0000-0000-000000000001', 'FIRST_LOG', 'Eco Pioneer', 'Earned when you log your first activity on the platform.', 'EcoPioneer', 'https://api.dicebear.com/7.x/identicon/svg?seed=EcoPioneer', 'LOG_COUNT', 1.0),
('a0000000-0000-0000-0000-000000000002', 'DIVERSE_LOGS', 'Eco Explorer', 'Log activities in at least 3 distinct categories.', 'EcoExplorer', 'https://api.dicebear.com/7.x/identicon/svg?seed=EcoExplorer', 'DIVERSE_CATEGORIES', 3.0),
('a0000000-0000-0000-0000-000000000003', 'THREE_GOALS', 'Consistently Green', 'Create a total of 3 or more carbon reduction goals.', 'ConsistentlyGreen', 'https://api.dicebear.com/7.x/identicon/svg?seed=ConsistentlyGreen', 'GOALS_COUNT', 3.0),
('a0000000-0000-0000-0000-000000000004', 'GOAL_ACHIEVED', 'Goal Getter', 'Successfully complete your first carbon reduction goal.', 'GoalGetter', 'https://api.dicebear.com/7.x/identicon/svg?seed=GoalGetter', 'GOALS_COMPLETED', 1.0),
('a0000000-0000-0000-0000-000000000005', 'CARBON_CUTTER_50', 'Carbon Crusader', 'Reduce a total of 50 kg or more CO2 emissions across completed goals.', 'CarbonCrusader', 'https://api.dicebear.com/7.x/identicon/svg?seed=CarbonCrusader', 'CARBON_REDUCED', 50.0),
('a0000000-0000-0000-0000-000000000006', 'LEADERBOARD_TOP_3', 'Low Emitter Elite', 'Reach top 3 low carbon emitters rank on the leaderboard.', 'LowEmitterElite', 'https://api.dicebear.com/7.x/identicon/svg?seed=LowEmitterElite', 'LEADERBOARD_RANK', 3.0);
