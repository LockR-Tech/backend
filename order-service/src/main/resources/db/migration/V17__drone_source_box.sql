-- Ô DRONE tại tủ gửi (Locker A) mà người gửi bỏ kiện vào để đội bay nạp lên drone.
--
-- Trước đây đơn drone chỉ giữ ô ở tủ nhận, nên ô drone người gửi vừa đặt ở tủ gửi
-- vẫn hiện "trống" và người khác đặt chồng lên được. Ô này được giữ từ lúc tạo đơn
-- tới khi kiện đã nạp lên drone (hoặc đơn bị huỷ).
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS source_box_id BIGINT;
