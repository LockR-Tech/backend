CREATE SCHEMA IF NOT EXISTS locker_schema;

CREATE TABLE locker_schema.lockers
(
    id          BIGSERIAL PRIMARY KEY,
    store_id    BIGINT,
    code        VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    status      VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    address     VARCHAR(500),
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    description VARCHAR(2000),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE TABLE locker_schema.locker_boxes
(
    id          BIGSERIAL PRIMARY KEY,
    locker_id   BIGINT      NOT NULL,
    box_number  INTEGER     NOT NULL,
    size        VARCHAR(30)          DEFAULT 'MEDIUM',
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    description VARCHAR(500),
    status      VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE INDEX idx_locker_boxes_locker_id ON locker_schema.locker_boxes (locker_id);

CREATE TABLE locker_schema.locker_reports
(
    id                  BIGSERIAL PRIMARY KEY,
    locker_id           BIGINT        NOT NULL,
    user_id             BIGINT        NOT NULL,
    title               VARCHAR(255)  NOT NULL,
    description         VARCHAR(2000) NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'OPEN',
    resolved_by_user_id BIGINT,
    resolved_at         TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP
);

CREATE INDEX idx_locker_reports_locker_id ON locker_schema.locker_reports (locker_id);
CREATE INDEX idx_locker_reports_user_id ON locker_schema.locker_reports (user_id);
