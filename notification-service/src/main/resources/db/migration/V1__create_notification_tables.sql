CREATE SCHEMA IF NOT EXISTS notification_schema;

CREATE TABLE notification_schema.notifications
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    title          VARCHAR(255) NOT NULL,
    message        TEXT         NOT NULL,
    type           VARCHAR(50)  NOT NULL DEFAULT 'SYSTEM',
    reference_id   BIGINT,
    reference_type VARCHAR(50),
    status         VARCHAR(20)  NOT NULL DEFAULT 'UNREAD',
    is_read        BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at        TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_schema.fcm_tokens
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(512) NOT NULL UNIQUE,
    device_type VARCHAR(30),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE INDEX idx_notifications_user_id ON notification_schema.notifications (user_id);
CREATE INDEX idx_fcm_tokens_user_id ON notification_schema.fcm_tokens (user_id);
