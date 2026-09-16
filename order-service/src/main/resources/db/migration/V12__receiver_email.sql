-- Email người nhận trên đơn gửi hàng, để gửi được mã mở tủ cho người CHƯA có tài khoản.
-- Trước đây chỉ có số điện thoại và mã chỉ tới được người nhận đã có tài khoản Lock.R
-- (gap F2-G03). Cột cho phép NULL nên đơn cũ và client cũ không bị ảnh hưởng.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS receiver_email VARCHAR(255);
