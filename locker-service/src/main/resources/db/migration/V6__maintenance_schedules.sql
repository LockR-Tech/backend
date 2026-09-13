-- L5 preventive maintenance: lịch kiểm tra định kỳ cho từng tủ.
CREATE TABLE locker_schema.maintenance_schedules
(
    id            BIGSERIAL PRIMARY KEY,
    locker_id     BIGINT       NOT NULL,
    title         VARCHAR(255) NOT NULL,
    interval_days INT          NOT NULL,
    last_done_at  TIMESTAMP,
    next_due_at   TIMESTAMP    NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP
);

CREATE INDEX idx_maintenance_schedules_locker ON locker_schema.maintenance_schedules (locker_id);
CREATE INDEX idx_maintenance_schedules_due ON locker_schema.maintenance_schedules (next_due_at);
