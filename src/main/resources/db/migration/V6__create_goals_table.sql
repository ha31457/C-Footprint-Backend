CREATE TABLE goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_reduction_percentage DOUBLE PRECISION NOT NULL,
    period_type VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    baseline_emission DOUBLE PRECISION NOT NULL,
    target_emission DOUBLE PRECISION NOT NULL,
    status VARCHAR(20) NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
