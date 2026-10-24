-- Đổi điểm lấy voucher + 1 điểm = 1đ (quyết định 2026-10-24, cùng đợt loyalty V5).
--
-- 1. Giá đổi bằng điểm của voucher. NULL/0 = mã thường (lưu miễn phí ở trang Ưu đãi); > 0 = chỉ có được
--    bằng cách đổi điểm, và chỉ người đã đổi (promotion_claims SAVED) mới áp được mã vào đơn.
ALTER TABLE order_schema.promotions
    ADD COLUMN IF NOT EXISTS points_cost INTEGER;

-- 2. loyalty-service nhân mọi số điểm với 100 (1 điểm cũ = 100đ = 100 điểm mới). Số điểm đơn đã dùng
--    chỉ để hiển thị nên đổi theo; points_discount là tiền, giữ nguyên.
UPDATE order_schema.orders
SET points_used = points_used * 100
WHERE points_used IS NOT NULL
  AND points_used > 0;
