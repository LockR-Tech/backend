-- Xoá ở trang admin là xoá mềm: tủ, ô tủ và drone vẫn còn trong DB kèm lý do, thời điểm và người xoá.
ALTER TABLE locker_schema.lockers
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN deleted_by BIGINT,
    ADD COLUMN deleted_reason VARCHAR(500);

ALTER TABLE locker_schema.locker_boxes
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN deleted_by BIGINT,
    ADD COLUMN deleted_reason VARCHAR(500);

ALTER TABLE locker_schema.drone_units
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN deleted_by BIGINT,
    ADD COLUMN deleted_reason VARCHAR(500);

-- Mã tủ, mã drone và mã ArUco của bản ghi đã xoá được dùng lại cho bản ghi mới.
ALTER TABLE locker_schema.lockers DROP CONSTRAINT lockers_code_key;
CREATE UNIQUE INDEX uq_lockers_code_not_deleted
    ON locker_schema.lockers (code)
    WHERE deleted_at IS NULL;

ALTER TABLE locker_schema.drone_units DROP CONSTRAINT drone_units_code_key;
CREATE UNIQUE INDEX uq_drone_units_code_active
    ON locker_schema.drone_units (code)
    WHERE active;

DROP INDEX locker_schema.idx_locker_boxes_aruco_unique;
CREATE UNIQUE INDEX idx_locker_boxes_aruco_unique
    ON locker_schema.locker_boxes (aruco_dictionary, aruco_marker_id)
    WHERE aruco_marker_id IS NOT NULL AND deleted_at IS NULL;
