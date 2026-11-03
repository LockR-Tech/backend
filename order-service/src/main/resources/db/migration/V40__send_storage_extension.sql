-- Gửi hàng qua tủ: người gửi chọn số giờ giữ hàng (lưu ở rental_duration_hours như đơn thuê) và gia hạn sau.
-- Gia hạn của đơn đã thanh toán chỉ có hiệu lực khi trả tiền: số giờ và số tiền chờ trả nằm ở hai cột dưới.
-- Chưa trả thì người nhận vẫn lấy hàng được và phần chờ trả bị bỏ khi đơn kết thúc.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS pending_extension_hours INTEGER;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS pending_extension_fee NUMERIC(12, 2);
