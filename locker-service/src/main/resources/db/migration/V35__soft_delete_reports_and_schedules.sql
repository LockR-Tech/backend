-- Xoá phiếu sự cố và lịch bảo trì ở trang admin là xoá mềm kèm lý do (ADR-0009): bản ghi, ảnh, nhật ký
-- sửa chữa và đánh giá vẫn còn trong DB, chỉ ẩn khỏi mọi danh sách.
ALTER TABLE locker_schema.locker_reports
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN deleted_by BIGINT,
    ADD COLUMN deleted_reason VARCHAR(500);

-- Lịch bảo trì vốn "xoá" bằng active = false; nay ghi thêm ai xoá, lúc nào, vì sao.
ALTER TABLE locker_schema.maintenance_schedules
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN deleted_by BIGINT,
    ADD COLUMN deleted_reason VARCHAR(500);
