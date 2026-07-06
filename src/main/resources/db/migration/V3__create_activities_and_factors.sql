CREATE TABLE emission_factor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category VARCHAR(50) NOT NULL,
    activity_type VARCHAR(100) UNIQUE NOT NULL,
    factor DOUBLE PRECISION NOT NULL,
    unit VARCHAR(20) NOT NULL
);

CREATE TABLE activity_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category VARCHAR(50) NOT NULL,
    activity_type VARCHAR(100) NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    unit VARCHAR(20) NOT NULL,
    co2_emission DOUBLE PRECISION NOT NULL,
    log_date DATE NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT fk_activity_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Seed default emission factors
INSERT INTO emission_factor (category, activity_type, factor, unit) VALUES
('transport', 'CAR_GASOLINE', 0.18, 'km'),
('transport', 'CAR_DIESEL', 0.17, 'km'),
('transport', 'PUBLIC_BUS', 0.08, 'km'),
('transport', 'FLIGHT', 0.20, 'km'),
('electricity', 'ELECTRICITY_GRID', 0.45, 'kWh'),
('electricity', 'ELECTRICITY_SOLAR', 0.05, 'kWh'),
('food', 'MEAL_MEAT', 2.5, 'servings'),
('food', 'MEAL_VEGETARIAN', 0.8, 'servings'),
('food', 'MEAL_VEGAN', 0.5, 'servings'),
('shopping', 'SHOPPING_CLOTHING', 0.5, 'USD'),
('shopping', 'SHOPPING_ELECTRONICS', 0.8, 'USD');
