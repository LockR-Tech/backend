-- Phụ thu khi đội bay cân kiện nặng hơn khối lượng khách khai báo lúc đặt đơn drone.
ALTER TABLE order_schema.drone_missions
    ADD COLUMN IF NOT EXISTS weight_surcharge NUMERIC(12, 2);
