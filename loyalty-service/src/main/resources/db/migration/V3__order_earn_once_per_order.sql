-- Điểm tự cộng khi đơn hoàn tất (loại ORDER_EARN): mỗi đơn đúng một lần, kể cả khi sự kiện
-- order.status.changed được giao lại hoặc xử lý song song. Điều chỉnh tay của admin không bị ràng buộc.
-- Dòng cũ admin tự gõ loại trùng tên (nếu có) đổi về ADJUSTMENT để index tạo được.
UPDATE loyalty_schema.point_transactions
SET type = 'ADJUSTMENT'
WHERE type = 'ORDER_EARN';

CREATE UNIQUE INDEX IF NOT EXISTS uq_point_transactions_order_earn
    ON loyalty_schema.point_transactions (order_id)
    WHERE type = 'ORDER_EARN';
