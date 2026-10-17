-- Dùng điểm thưởng thanh toán đơn + voucher theo tủ (hợp đồng điểm/voucher 2026-10-17).
--
-- points_used / points_discount: số điểm khách dùng và số tiền đơn được giảm nhờ điểm (tính trên phần
-- còn lại sau mã giảm giá). total_price = max(original_price − discount − points_discount, 0).
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS points_used INT NOT NULL DEFAULT 0;
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS points_discount NUMERIC(12, 2) NOT NULL DEFAULT 0;
-- Lúc loyalty-service xác nhận đã hoàn điểm cho đơn huỷ/hoàn tiền; NULL khi đơn có dùng điểm mà chưa
-- hoàn được (loyalty tạm lỗi) — job định kỳ thử lại.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS points_refunded_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_orders_points_refund_pending
    ON order_schema.orders (id)
    WHERE points_used > 0 AND points_refunded_at IS NULL;

-- Voucher theo tủ: lọc theo tủ ở app và trang admin.
CREATE INDEX IF NOT EXISTS idx_promotions_locker_id ON order_schema.promotions (locker_id);
