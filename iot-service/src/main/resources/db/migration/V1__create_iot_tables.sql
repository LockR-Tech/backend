CREATE SCHEMA IF NOT EXISTS iot_schema;

CREATE TABLE iot_schema.device_statuses
(
    id           BIGSERIAL PRIMARY KEY,
    device_id    VARCHAR(100) NOT NULL UNIQUE,
    locker_id    BIGINT,
    status       VARCHAR(30)  NOT NULL DEFAULT 'UNKNOWN',
    last_seen_at TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP
);
