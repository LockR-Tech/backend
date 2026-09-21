-- Luồng 4 (KTV tủ): KTV phụ trách tủ, định tuyến phiếu sự cố, khôi phục ô/tủ/bãi đáp khi đóng phiếu,
-- kết quả kiểm tra định kỳ và nhắc hạn.

-- KTV tủ phụ trách; NULL = phiếu của tủ được báo cho mọi KTV tủ.
-- maintenance_source: ai đưa tủ vào MAINTENANCE (ADMIN bật tay / TICKET do phiếu chặn tủ).
ALTER TABLE locker_schema.lockers
    ADD COLUMN IF NOT EXISTS assigned_technician_id BIGINT,
    ADD COLUMN IF NOT EXISTS maintenance_source VARCHAR(20);

UPDATE locker_schema.lockers
SET maintenance_source = 'ADMIN'
WHERE status = 'MAINTENANCE' AND maintenance_source IS NULL;

-- routed_to_user_id: KTV được báo khi phiếu còn OPEN. schedule_id: phiếu sinh từ lần kiểm tra định kỳ KHÔNG ĐẠT.
-- category: BOX / DRONE / LANDING_PAD / LOCKER. blocks_locker: phiếu đưa cả tủ vào MAINTENANCE.
ALTER TABLE locker_schema.locker_reports
    ADD COLUMN IF NOT EXISTS routed_to_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS schedule_id BIGINT,
    ADD COLUMN IF NOT EXISTS category VARCHAR(20),
    ADD COLUMN IF NOT EXISTS blocks_locker BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE locker_schema.locker_reports
SET category = CASE
        WHEN box_id IS NOT NULL THEN 'BOX'
        WHEN drone_unit_id IS NOT NULL THEN 'DRONE'
        WHEN title LIKE 'Bãi đáp drone%' THEN 'LANDING_PAD'
        ELSE 'LOCKER'
    END
WHERE category IS NULL;

CREATE INDEX IF NOT EXISTS idx_locker_reports_routed_to
    ON locker_schema.locker_reports (routed_to_user_id, status);
CREATE INDEX IF NOT EXISTS idx_lockers_assigned_technician
    ON locker_schema.lockers (assigned_technician_id);

-- Trạng thái ô trước khi báo hỏng (OCCUPIED/RESERVED/...) để trả lại đúng khi sửa xong; NULL = AVAILABLE.
ALTER TABLE locker_schema.locker_boxes
    ADD COLUMN IF NOT EXISTS pre_fault_status VARCHAR(30);

-- pending_report_id: phiếu sinh từ lần kiểm tra KHÔNG ĐẠT, hạn kế tiếp chỉ dời khi phiếu này đóng.
-- last_result: PASSED / FAILED của lần kiểm tra gần nhất. last_due_notified_at: đã nhắc hạn cho kỳ hiện tại.
ALTER TABLE locker_schema.maintenance_schedules
    ADD COLUMN IF NOT EXISTS pending_report_id BIGINT,
    ADD COLUMN IF NOT EXISTS last_result VARCHAR(20),
    ADD COLUMN IF NOT EXISTS last_due_notified_at TIMESTAMP;
