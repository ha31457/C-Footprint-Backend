CREATE TABLE organization (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL
);

ALTER TABLE users ADD COLUMN organization_id UUID;
ALTER TABLE users ADD COLUMN is_temp_password BOOLEAN DEFAULT FALSE;

ALTER TABLE users ADD CONSTRAINT fk_users_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE SET NULL;
