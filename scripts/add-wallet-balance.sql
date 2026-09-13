-- ========================================================
-- Script nạp tiền vào ví cho tài khoản test
-- YÊU CẦU: Hãy chạy script này trong database: payment_db
-- ========================================================

SET
search_path TO payment_schema;

-- Nạp 500.000 VNĐ cho tài khoản Quốc Bảo Huy (ID: 9002)
INSERT INTO payment_schema.wallets (user_id, balance, currency, version, created_at, updated_at)
VALUES (9002,
        500000.00, 'VND', 1, NOW(), NOW()) ON CONFLICT (user_id) DO
UPDATE SET
    balance = 500000.00,
    updated_at = NOW(),
    version = payment_schema.wallets.version + 1;

