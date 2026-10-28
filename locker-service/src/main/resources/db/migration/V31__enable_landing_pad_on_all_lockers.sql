-- Tất cả các tủ trên hệ thống đều được trang bị bãi đáp drone.
-- Giữ nguyên trạng thái bảo trì/lỗi hiện có của tủ (status) và của bãi đáp (landing_pad_status).
ALTER TABLE locker_schema.lockers
    ALTER COLUMN landing_pad SET DEFAULT TRUE;

UPDATE locker_schema.lockers
SET landing_pad       = TRUE,
    landing_marker_id = COALESCE(landing_marker_id, 'ARUCO-' || id),
    updated_at        = CURRENT_TIMESTAMP
WHERE landing_pad = FALSE;

-- Với những tủ trước đây chưa có ô DRONE nào, chuyển các ô đang trống ở hàng 1 (dưới bãi đáp) sang ô DRONE
-- để tủ có thể nhận đơn drone ngay khi bãi đáp và tủ ở trạng thái hoạt động.
UPDATE locker_schema.locker_boxes b
SET cell_type   = 'DRONE',
    size        = 'MEDIUM',
    description = 'Ô nhận hàng drone (dưới nắp trượt)',
    updated_at  = CURRENT_TIMESTAMP
WHERE b.row_index = 1
  AND b.status = 'AVAILABLE'
  AND NOT EXISTS (
      SELECT 1
      FROM locker_schema.locker_boxes d
      WHERE d.locker_id = b.locker_id
        AND d.cell_type = 'DRONE'
  );

