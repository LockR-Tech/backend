-- Bổ sung thông tin tài khoản ngân hàng và xử lý duyệt hoàn tiền vào bảng refunds
ALTER TABLE payment_schema.refunds
    ADD COLUMN IF NOT EXISTS bank_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bank_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS account_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS account_holder_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS bank_transfer_ref VARCHAR(100);

-- Bảng lưu thông tin tài khoản ngân hàng mặc định của khách hàng
CREATE TABLE IF NOT EXISTS payment_schema.user_bank_accounts
(
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT         NOT NULL UNIQUE,
    bank_name           VARCHAR(100)   NOT NULL,
    bank_code           VARCHAR(20)    NOT NULL,
    account_number      VARCHAR(50)    NOT NULL,
    account_holder_name VARCHAR(100)   NOT NULL,
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_bank_accounts_user_id ON payment_schema.user_bank_accounts(user_id);
