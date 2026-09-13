CREATE SCHEMA IF NOT EXISTS loyalty_schema;

CREATE TABLE loyalty_schema.loyalty_accounts
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE,
    points     INTEGER     NOT NULL DEFAULT 0,
    stamps     INTEGER     NOT NULL DEFAULT 0,
    tier       VARCHAR(30) NOT NULL DEFAULT 'BRONZE',
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE loyalty_schema.point_transactions
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    order_id   BIGINT,
    points     INTEGER     NOT NULL,
    type       VARCHAR(30) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_point_transactions_user_id ON loyalty_schema.point_transactions (user_id);
