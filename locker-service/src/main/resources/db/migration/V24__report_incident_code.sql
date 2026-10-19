-- Bổ sung mã sự cố (incident_code) vào locker_reports để liên kết hai chiều giữa phiếu bảo trì và sự cố rơi kiện
ALTER TABLE locker_schema.locker_reports
    ADD COLUMN IF NOT EXISTS incident_code VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_locker_reports_incident_code
    ON locker_schema.locker_reports(incident_code);
