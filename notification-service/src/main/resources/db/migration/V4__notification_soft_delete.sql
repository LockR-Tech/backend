-- Admin xoá thông báo là xoá mềm: còn trong DB kèm lý do, thời điểm và người xoá (ADR-0009).
ALTER TABLE notification_schema.notifications
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN deleted_by BIGINT,
    ADD COLUMN deleted_reason VARCHAR(500);
