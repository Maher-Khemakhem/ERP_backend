CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.stores (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    plan VARCHAR(40) NOT NULL DEFAULT 'STANDARD'
);

CREATE TABLE IF NOT EXISTS platform.store_users (
    store_id UUID NOT NULL REFERENCES platform.stores(id),
    user_id UUID NOT NULL,
    role VARCHAR(40) NOT NULL,
    PRIMARY KEY (store_id, user_id, role)
);

CREATE INDEX IF NOT EXISTS idx_store_users_user_id
    ON platform.store_users (user_id);

INSERT INTO platform.stores (id, name, created_at, status, plan)
SELECT '00000000-0000-0000-0000-000000000001', 'Default Store', CURRENT_TIMESTAMP, 'ACTIVE', 'STANDARD'
WHERE NOT EXISTS (SELECT 1 FROM platform.stores);