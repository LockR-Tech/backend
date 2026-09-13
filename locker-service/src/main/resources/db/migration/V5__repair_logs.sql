-- L5 maintenance work-log: technicians append progress notes to a report.
CREATE TABLE locker_schema.repair_logs
(
    id            BIGSERIAL PRIMARY KEY,
    report_id     BIGINT        NOT NULL,
    actor_user_id BIGINT,
    note          VARCHAR(2000) NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_repair_logs_report_id ON locker_schema.repair_logs (report_id);
