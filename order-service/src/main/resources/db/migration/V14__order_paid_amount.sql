-- Số tiền khách đã thực trả cho đơn.
--
-- Gia hạn thuê tủ và tính phí quá hạn đều cộng thêm vào `total_price` rồi đặt
-- `payment_status = 'UNPAID'`, nên luồng thanh toán thu lại **toàn bộ** đơn —
-- kể cả phần khách đã trả trước đó. Cột này cho phép chỉ thu phần còn thiếu.
ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(19, 2) NOT NULL DEFAULT 0;

-- Đơn đã thanh toán trước khi có cột này coi như đã trả đủ tổng hiện tại.
UPDATE order_schema.orders
SET paid_amount = COALESCE(total_price, 0)
WHERE payment_status = 'PAID'
  AND paid_amount = 0;
