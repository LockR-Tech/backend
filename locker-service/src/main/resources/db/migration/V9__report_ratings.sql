CREATE TABLE locker_schema.locker_report_ratings
(
    id         BIGSERIAL PRIMARY KEY,
    report_id  BIGINT    NOT NULL UNIQUE,
    user_id    BIGINT    NOT NULL,
    rating     INT       NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
