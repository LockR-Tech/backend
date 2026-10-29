-- Tủ có ô DRONE (ô nhận hàng drone thả xuống) thì có bãi đáp drone.
-- Dữ liệu cũ có tủ đã cấu hình ô DRONE nhưng landing_pad vẫn FALSE (vd. Locker2309): app đặt đơn
-- báo "Chưa có bãi đáp drone" dù tủ còn nguyên ô nhận drone trống. Từ bản này, thêm / đổi ô sang
-- DRONE cũng tự bật bãi đáp (LockerService), migration này đồng bộ phần dữ liệu đã có.
UPDATE locker_schema.lockers
SET landing_pad = TRUE,
    landing_pad_status = COALESCE(NULLIF(landing_pad_status, ''), 'OK'),
    updated_at = CURRENT_TIMESTAMP
WHERE landing_pad = FALSE
  AND id IN (
      SELECT DISTINCT locker_id
      FROM locker_schema.locker_boxes
      WHERE UPPER(cell_type) = 'DRONE'
  );
