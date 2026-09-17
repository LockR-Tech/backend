-- Thêm các cột theo dõi gia hạn SLA cho phiếu sự cố
ALTER TABLE locker_reports
    ADD COLUMN IF NOT EXISTS sla_due_at TIMESTAMP;
ALTER TABLE locker_reports
    ADD COLUMN IF NOT EXISTS sla_extended_hours INT DEFAULT 0;
ALTER TABLE locker_reports
    ADD COLUMN IF NOT EXISTS sla_extension_reason VARCHAR(1000);

-- Backfill sla_due_at cho các phiếu cũ chưa có (mặc định 4 tiếng từ thời điểm tạo)
UPDATE locker_reports
SET sla_due_at = created_at + INTERVAL '4 hour'
WHERE sla_due_at IS NULL AND created_at IS NOT NULL;
