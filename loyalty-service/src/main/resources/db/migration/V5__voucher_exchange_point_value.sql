-- Đổi điểm lấy voucher + 1 điểm = 1đ (quyết định 2026-10-24).
--
-- 1. Mã của một lần đổi voucher (order-service sinh). Mỗi lần đổi trừ điểm một lần và hoàn điểm tối
--    đa một lần, kể cả khi order-service gọi lại hoặc gọi song song.
ALTER TABLE loyalty_schema.point_transactions
    ADD COLUMN IF NOT EXISTS reference VARCHAR(64);

-- Dòng cũ admin tự gõ trùng tên loại mới (nếu có) là điều chỉnh tay.
UPDATE loyalty_schema.point_transactions
SET type = 'ADJUSTMENT'
WHERE type IN ('VOUCHER_EXCHANGE', 'VOUCHER_EXCHANGE_REFUND');

CREATE UNIQUE INDEX IF NOT EXISTS uq_point_transactions_voucher_exchange
    ON loyalty_schema.point_transactions (reference)
    WHERE type = 'VOUCHER_EXCHANGE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_point_transactions_voucher_exchange_refund
    ON loyalty_schema.point_transactions (reference)
    WHERE type = 'VOUCHER_EXCHANGE_REFUND';

-- 2. Trước: tích 1 điểm mỗi 1.000đ, 1 điểm = 100đ khi dùng. Nay: tích 1 điểm mỗi 10đ (10%), 1 điểm = 1đ.
--    1 điểm cũ = 100 điểm mới nên mọi số điểm nhân 100 — giá trị tiền khách đang có giữ nguyên, hạng giữ
--    nguyên (mốc hạng cũng nhân 100). Cột `amount` là tiền nên không đổi.
UPDATE loyalty_schema.loyalty_accounts
SET points          = points * 100,
    lifetime_points = lifetime_points * 100;

UPDATE loyalty_schema.point_transactions
SET points = points * 100;

-- Giá trị admin đã ghi đè (key không có dòng nào thì dùng mặc định mới trong code).
UPDATE loyalty_schema.system_settings
SET setting_value = GREATEST(1, ROUND(setting_value::NUMERIC / 100))::BIGINT::TEXT,
    updated_at    = CURRENT_TIMESTAMP
WHERE setting_key IN ('app.loyalty.vnd-per-point', 'app.loyalty.redeem-vnd-per-point')
  AND setting_value ~ '^[0-9]+$';

UPDATE loyalty_schema.system_settings
SET setting_value = LEAST(10000000, setting_value::BIGINT * 100)::TEXT,
    updated_at    = CURRENT_TIMESTAMP
WHERE setting_key IN ('app.loyalty.tier-silver-points', 'app.loyalty.tier-gold-points',
                      'app.loyalty.tier-platinum-points', 'app.loyalty.redeem-min-points',
                      'app.loyalty.points-reward-cost')
  AND setting_value ~ '^[0-9]+$';
