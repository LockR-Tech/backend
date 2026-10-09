-- Khai báo kiện hàng của đơn drone: kích thước (ô DRONE và khoang drone có giới hạn), loại hàng,
-- giá trị khai báo (căn cứ bồi thường), dễ vỡ, và thời điểm người gửi cam kết không gửi hàng cấm.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS parcel_length_cm INTEGER;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS parcel_width_cm INTEGER;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS parcel_height_cm INTEGER;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS parcel_category VARCHAR(30);
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS parcel_declared_value NUMERIC(12, 2);
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS parcel_fragile BOOLEAN;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS prohibited_items_declared_at TIMESTAMP;
-- Khoảng cách đường chim bay tủ gửi → tủ nhận lúc đặt đơn.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS route_distance_meters INTEGER;

-- Kiện đang ở đâu: người gửi xác nhận đã bỏ kiện vào ô gửi; đơn kết thúc mà không giao được
-- thì đội bay/admin xác nhận đã trả kiện cho người gửi.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS parcel_dropped_at TIMESTAMP;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS parcel_returned_at TIMESTAMP;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS parcel_returned_by_user_id BIGINT;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS parcel_return_note VARCHAR(500);

-- Lần thanh toán gần nhất đã cộng vào paid_amount của đơn drone: sự kiện payment.completed
-- lặp lại (VNPay return + IPN) không được cộng hai lần.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS last_payment_id BIGINT;

-- Đơn drone đã có kiện trong hệ thống trước bản này (đội bay đã tiếp nhận): coi như đã bỏ kiện,
-- để chúng không bị quy tắc "phải xác nhận bỏ kiện trước khi tiếp nhận" chặn giữa chừng.
UPDATE order_schema.orders o
SET parcel_dropped_at = COALESCE(o.paid_at, o.created_at)
WHERE o.type = 'DRONE_DELIVERY'
  AND o.parcel_dropped_at IS NULL
  AND EXISTS (SELECT 1 FROM order_schema.drone_missions m WHERE m.order_id = o.id);

ALTER TABLE order_schema.drone_missions
    ADD COLUMN IF NOT EXISTS battery_percent_at_launch INTEGER;
ALTER TABLE order_schema.drone_missions
    ADD COLUMN IF NOT EXISTS landed_at TIMESTAMP;
ALTER TABLE order_schema.drone_missions
    ADD COLUMN IF NOT EXISTS deposited_at TIMESTAMP;
ALTER TABLE order_schema.drone_missions
    ADD COLUMN IF NOT EXISTS deposited_by_user_id BIGINT;
