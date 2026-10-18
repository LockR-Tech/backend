-- Dùng điểm thanh toán đơn + hạng theo tổng điểm đã tích (hợp đồng điểm/voucher 2026-10-17).
--
-- 1. lifetime_points: tổng điểm đã TÍCH từ trước tới nay. Cộng khi ORDER_EARN và điều chỉnh tay
--    dương, trừ khi điều chỉnh tay âm (không xuống dưới 0); KHÔNG đổi khi dùng điểm (ORDER_REDEEM,
--    REDEEM) hay hoàn điểm đã dùng (ORDER_REDEEM_REFUND). Hạng tính theo cột này nên tiêu điểm
--    không làm tụt hạng.
ALTER TABLE loyalty_schema.loyalty_accounts
    ADD COLUMN IF NOT EXISTS lifetime_points INTEGER NOT NULL DEFAULT 0;

-- Hai loại mới chỉ dành cho order-service. Dòng cũ admin tự gõ trùng tên (nếu có) là điều chỉnh tay.
UPDATE loyalty_schema.point_transactions
SET type = 'ADJUSTMENT'
WHERE type IN ('ORDER_REDEEM', 'ORDER_REDEEM_REFUND');

-- Dữ liệu cũ: tổng mọi giao dịch tích/điều chỉnh (mọi loại trừ các loại dùng/hoàn điểm), không
-- nhỏ hơn số dư hiện tại và không âm.
UPDATE loyalty_schema.loyalty_accounts a
SET lifetime_points = GREATEST(
        a.points,
        0,
        COALESCE((SELECT SUM(t.points)
                  FROM loyalty_schema.point_transactions t
                  WHERE t.user_id = a.user_id
                    AND t.type NOT IN ('ORDER_REDEEM', 'ORDER_REDEEM_REFUND', 'REDEEM')), 0));

-- 2. Giá trị tiền của giao dịch dùng điểm (số tiền đã giảm cho đơn) — gọi lại theo cùng orderId
--    trả đúng kết quả cũ dù admin đã đổi giá trị một điểm. Giao dịch khác để trống.
ALTER TABLE loyalty_schema.point_transactions
    ADD COLUMN IF NOT EXISTS amount NUMERIC(14, 2);

-- 3. Mỗi đơn chỉ trừ điểm một lần và chỉ hoàn điểm một lần, kể cả khi order-service gọi lại
--    hoặc gọi song song.
CREATE UNIQUE INDEX IF NOT EXISTS uq_point_transactions_order_redeem
    ON loyalty_schema.point_transactions (order_id)
    WHERE type = 'ORDER_REDEEM';

CREATE UNIQUE INDEX IF NOT EXISTS uq_point_transactions_order_redeem_refund
    ON loyalty_schema.point_transactions (order_id)
    WHERE type = 'ORDER_REDEEM_REFUND';
