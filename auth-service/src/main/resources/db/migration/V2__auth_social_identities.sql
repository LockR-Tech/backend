CREATE TABLE auth_schema.social_identities
(
    id               BIGSERIAL PRIMARY KEY,
    account_id       BIGINT       NOT NULL REFERENCES auth_schema.auth_accounts (id),
    provider         VARCHAR(40)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, provider_user_id)
);
CREATE INDEX idx_social_identities_account ON auth_schema.social_identities (account_id);
