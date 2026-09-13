CREATE SCHEMA IF NOT EXISTS store_schema;

CREATE TABLE store_schema.stores
(
    id            BIGSERIAL PRIMARY KEY,
    partner_id    BIGINT,
    name          VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(50),
    address       VARCHAR(500),
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    image         VARCHAR(1000),
    description   VARCHAR(2000),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    status        VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP
);

CREATE INDEX idx_stores_partner_id ON store_schema.stores (partner_id);
