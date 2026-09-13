CREATE TABLE IF NOT EXISTS user_schema.audit_logs
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    user_id
    BIGINT,
    action
    VARCHAR
(
    80
) NOT NULL,
    entity_type VARCHAR
(
    80
),
    entity_id BIGINT,
    description VARCHAR
(
    2000
),
    status VARCHAR
(
    30
) NOT NULL DEFAULT 'SUCCESS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON user_schema.audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON user_schema.audit_logs(entity_type, entity_id);
