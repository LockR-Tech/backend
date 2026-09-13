CREATE SCHEMA IF NOT EXISTS auth_schema;

CREATE TABLE auth_schema.auth_accounts
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL UNIQUE,
    email          VARCHAR(255) UNIQUE,
    phone_number   VARCHAR(50) UNIQUE,
    password_hash  VARCHAR(500) NOT NULL,
    auth_provider  VARCHAR(30)  NOT NULL DEFAULT 'LOCAL',
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    phone_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    status         VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at  TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE TABLE auth_schema.refresh_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT       NOT NULL,
    token_hash VARCHAR(500) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE auth_schema.email_otps
(
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    otp_hash   VARCHAR(500) NOT NULL,
    purpose    VARCHAR(50)  NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_account ON auth_schema.refresh_tokens (account_id);
CREATE INDEX idx_email_otps_email ON auth_schema.email_otps (email);
