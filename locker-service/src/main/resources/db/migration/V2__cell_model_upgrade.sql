-- Nâng cấp mô hình ô tủ: loại ô (drone/thường/vali), vị trí vật lý, trạng thái hỏng,
-- và thông tin bãi đáp drone trên nóc tủ. Xem LOCKER_FLOW_PLAN.md.

ALTER TABLE locker_schema.locker_boxes
    ADD COLUMN cell_type VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN row_index INTEGER,
    ADD COLUMN col_index INTEGER,
    ADD COLUMN fault_reason VARCHAR(500);

ALTER TABLE locker_schema.lockers
    ADD COLUMN landing_pad BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN landing_marker_id VARCHAR(50);

CREATE INDEX idx_locker_boxes_cell_type ON locker_schema.locker_boxes (locker_id, cell_type, status);
