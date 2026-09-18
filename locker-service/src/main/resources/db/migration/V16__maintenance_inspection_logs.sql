-- Bổ sung các cột mở rộng cho lịch kiểm tra định kỳ
ALTER TABLE locker_schema.maintenance_schedules
    ADD COLUMN IF NOT EXISTS assigned_technician_id BIGINT,
    ADD COLUMN IF NOT EXISTS priority VARCHAR(20) DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS description VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS checklist TEXT;

CREATE INDEX IF NOT EXISTS idx_maintenance_schedules_tech
    ON locker_schema.maintenance_schedules (assigned_technician_id);

-- Bảng nhật ký / lịch sử các lượt kiểm tra định kỳ thực tế của KTV
CREATE TABLE IF NOT EXISTS locker_schema.maintenance_inspection_logs
(
    id                BIGSERIAL PRIMARY KEY,
    schedule_id       BIGINT       NOT NULL,
    locker_id         BIGINT,
    drone_unit_id     BIGINT,
    technician_id     BIGINT,
    technician_name   VARCHAR(255),
    status            VARCHAR(50)  NOT NULL DEFAULT 'PASSED',
    note              VARCHAR(2000),
    photo_urls        TEXT,
    checklist_results TEXT,
    created_report_id BIGINT,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inspection_schedule
        FOREIGN KEY (schedule_id) REFERENCES locker_schema.maintenance_schedules (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_inspection_logs_schedule ON locker_schema.maintenance_inspection_logs (schedule_id);
CREATE INDEX IF NOT EXISTS idx_inspection_logs_locker ON locker_schema.maintenance_inspection_logs (locker_id);
CREATE INDEX IF NOT EXISTS idx_inspection_logs_tech ON locker_schema.maintenance_inspection_logs (technician_id);
CREATE INDEX IF NOT EXISTS idx_inspection_logs_created ON locker_schema.maintenance_inspection_logs (created_at);
