-- Số lần KTV đang giữ phiếu đã tự gia hạn SLA. Giới hạn số lần và số giờ mỗi lần do admin cấu hình
-- (app.maintenance.sla-max-extensions, app.maintenance.sla-max-hours-per-extension).
-- Lần gia hạn của admin không tính vào cột này.
ALTER TABLE locker_schema.locker_reports
    ADD COLUMN IF NOT EXISTS sla_extension_count INT NOT NULL DEFAULT 0;
