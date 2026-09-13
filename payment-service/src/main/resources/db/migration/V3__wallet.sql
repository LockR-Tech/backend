-- Internal prepaid wallet: a per-user balance + an immutable ledger of movements.
CREATE TABLE payment_schema.wallets
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT         NOT NULL UNIQUE,
    balance    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    currency   VARCHAR(10)    NOT NULL DEFAULT 'VND',
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE payment_schema.wallet_transactions
(
    id            BIGSERIAL PRIMARY KEY,
    wallet_id     BIGINT         NOT NULL REFERENCES payment_schema.wallets (id),
    user_id       BIGINT         NOT NULL,
    type          VARCHAR(10)    NOT NULL, -- CREDIT | DEBIT
    amount        NUMERIC(14, 2) NOT NULL,
    balance_after NUMERIC(14, 2) NOT NULL,
    source        VARCHAR(30)    NOT NULL, -- TOPUP | ORDER_PAYMENT | REFUND | ADJUST
    reference_id  VARCHAR(255),
    description   VARCHAR(500),
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wallet_tx_wallet ON payment_schema.wallet_transactions (wallet_id);
CREATE INDEX idx_wallet_tx_user ON payment_schema.wallet_transactions (user_id);
-- Idempotency guard: a movement for a given (source, reference_id) is applied at most once
-- (e.g. VNPay return + IPN both firing for the same topup must credit only once).
CREATE UNIQUE INDEX uq_wallet_tx_source_ref
    ON payment_schema.wallet_transactions (source, reference_id) WHERE reference_id IS NOT NULL;
