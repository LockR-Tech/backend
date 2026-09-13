CREATE TABLE iot_schema.box_access_logs
(
    id              BIGSERIAL PRIMARY KEY,
    box_id          BIGINT      NOT NULL,
    locker_id       BIGINT,
    order_id        BIGINT,
    actor_user_id   BIGINT,
    credential_type VARCHAR(20) NOT NULL,
    result          VARCHAR(20) NOT NULL,
    message         VARCHAR(255),
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_box_access_logs_box_id ON iot_schema.box_access_logs (box_id);
CREATE INDEX idx_box_access_logs_created_at ON iot_schema.box_access_logs (created_at DESC);

CREATE TABLE iot_schema.access_attempts
(
    box_id       BIGINT PRIMARY KEY,
    failed_count INT       NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
