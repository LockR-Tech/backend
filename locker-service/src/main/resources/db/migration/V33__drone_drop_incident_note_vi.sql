-- Lý do lỗi drone và nhật ký bảo trì do order-service ghi khi báo rơi kiện từng là tiếng Anh
-- "Parcel drop incident DPI-…" (hiện thẳng trên app KTV và trang admin, test 2026-11-02). Đổi sang tiếng Việt
-- cho khớp chuỗi order-service ghi từ nay.
UPDATE locker_schema.drone_units
SET fault_reason = REPLACE(fault_reason, 'Parcel drop incident', 'Sự cố rơi kiện')
WHERE fault_reason LIKE '%Parcel drop incident%';

UPDATE locker_schema.drone_maintenance_logs
SET note = REPLACE(note, 'Parcel drop incident', 'Sự cố rơi kiện')
WHERE note LIKE '%Parcel drop incident%';
