-- Đơn drone chỉ giao VÀO tủ nhận: ô khách chọn lúc đặt là ô NHẬN. Trước đây ô này bị ghi
-- vào send_box_id nên trang admin hiện "Ô gửi: Ô 2" và "Ô nhận: —". Chuyển dữ liệu cũ sang
-- receive_box_id; reserved_box_id giữ nguyên (luồng drone vẫn đọc reserved trước).
UPDATE order_schema.orders
SET receive_box_id = COALESCE(receive_box_id, send_box_id),
    send_box_id    = NULL
WHERE type = 'DRONE_DELIVERY'
  AND send_box_id IS NOT NULL
  AND (reserved_box_id IS NULL OR send_box_id = reserved_box_id);
