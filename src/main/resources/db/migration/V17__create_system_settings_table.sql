CREATE TABLE system_settings (
    config_key VARCHAR(50) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL
);

INSERT INTO system_settings (config_key, config_value) VALUES
('leaderboard_enabled', 'true'),
('badges_enabled', 'true'),
('google_signin_enabled', 'true');
