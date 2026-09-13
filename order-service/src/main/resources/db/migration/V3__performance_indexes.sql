-- Tối ưu DB: index cho các truy vấn nóng của order-service (chỉ thêm index,
-- không đổi dữ liệu). Đối chiếu repository:
--   LockerOrderRepository.findByPinCode  -> tra cứu đơn theo PIN khi mở tủ/lấy
--     hàng (getByAccess, IoT verify-access) — rất nóng, trước đây full-scan.
--   findByUserIdOrderByCreatedAtDesc     -> "đơn của tôi" (đã có idx user_id,
--     thêm composite để khỏi sort).
--   OrderComplaintRepository/OrderRatingRepository.findByUserId... -> "khiếu
--     nại/đánh giá của tôi".
CREATE INDEX IF NOT EXISTS idx_orders_pin_code ON order_schema.orders(pin_code);
CREATE INDEX IF NOT EXISTS idx_orders_user_created ON order_schema.orders(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_complaints_user_id ON order_schema.order_complaints(user_id);
CREATE INDEX IF NOT EXISTS idx_order_ratings_user_id ON order_schema.order_ratings(user_id);
