CREATE TABLE payment_schema.withdrawal_requests
(
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT         NOT NULL,
    amount               NUMERIC(14, 2) NOT NULL,
    bank_name            VARCHAR(100)   NOT NULL,
    bank_code            VARCHAR(20)    NOT NULL,
    account_number       VARCHAR(50)    NOT NULL,
    account_holder_name  VARCHAR(100)   NOT NULL,
    status               VARCHAR(20)    NOT NULL DEFAULT 'PENDING', -- PENDING | COMPLETED | REJECTED
    reference_id         VARCHAR(100)   NOT NULL UNIQUE,
    transaction_id       BIGINT REFERENCES payment_schema.wallet_transactions (id),
    rejection_reason     VARCHAR(500),
    processed_by_user_id BIGINT,
    created_at           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at         TIMESTAMP
);

CREATE INDEX idx_withdrawal_user ON payment_schema.withdrawal_requests (user_id);
CREATE INDEX idx_withdrawal_status ON payment_schema.withdrawal_requests (status);
