-- Bổ sung trường thông tin vị trí cụ thể và khung giờ ca kiểm tra cho kế hoạch bảo trì định kỳ
ALTER TABLE locker_schema.maintenance_schedules
    ADD COLUMN IF NOT EXISTS location_note VARCHAR(500),
    ADD COLUMN IF NOT EXISTS scheduled_time_slot VARCHAR(100);
