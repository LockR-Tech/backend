-- Gắn phiếu sự cố với đơn hàng cụ thể (nếu sự cố xảy ra trong lúc đơn đang dùng ô).
-- Giúp tách biệt rõ sự cố của đơn cũ với đơn mới tạo tại cùng một ô tủ.
ALTER TABLE locker_schema.locker_reports
    ADD COLUMN IF NOT EXISTS order_id BIGINT,
    ADD COLUMN IF NOT EXISTS order_code VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_locker_reports_order_id
    ON locker_schema.locker_reports (order_id);

CREATE INDEX IF NOT EXISTS idx_locker_reports_order_code
    ON locker_schema.locker_reports (order_code);
