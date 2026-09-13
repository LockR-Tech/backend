CREATE SCHEMA IF NOT EXISTS payment_schema;

CREATE TABLE payment_schema.payments
(
    id                       BIGSERIAL PRIMARY KEY,
    order_id                 BIGINT         NOT NULL,
    user_id                  BIGINT         NOT NULL,
    amount                   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    method                   VARCHAR(30)    NOT NULL DEFAULT 'CASH',
    status                   VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    reference_id             VARCHAR(255) UNIQUE,
    reference_transaction_id VARCHAR(255),
    content                  VARCHAR(500),
    qr                       VARCHAR(1000),
    url                      VARCHAR(1000),
    deeplink                 VARCHAR(1000),
    description              VARCHAR(1000),
    created_at               TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP
);

CREATE INDEX idx_payments_order_id ON payment_schema.payments (order_id);
CREATE INDEX idx_payments_user_id ON payment_schema.payments (user_id);

CREATE TABLE payment_schema.refunds
(
    id                   BIGSERIAL PRIMARY KEY,
    payment_id           BIGINT         NOT NULL,
    order_id             BIGINT         NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL,
    status               VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    reason               VARCHAR(2000),
    transaction_id       VARCHAR(100),
    processed_by_user_id BIGINT,
    requested_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at         TIMESTAMP
);

CREATE INDEX idx_refunds_order_id ON payment_schema.refunds (order_id);
