-- Maintenance module: link reports to a specific box and allow technicians to claim work
ALTER TABLE locker_reports
    ADD COLUMN IF NOT EXISTS box_id BIGINT;
ALTER TABLE locker_reports
    ADD COLUMN IF NOT EXISTS assigned_to_user_id BIGINT;
ALTER TABLE locker_reports
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_locker_reports_status ON locker_reports (status);
CREATE INDEX IF NOT EXISTS idx_locker_reports_assignee ON locker_reports (assigned_to_user_id);
