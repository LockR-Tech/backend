-- Khách phải bấm "Đồng ý" Điều khoản dịch vụ ở lần đăng nhập đầu tiên (app mobile).
-- terms_accepted: false = chưa đồng ý (mọi tài khoản cũ bắt đầu ở false ⇒ app hỏi lại một lần).
-- terms_version: phiên bản điều khoản người dùng đã đồng ý, để sau này đổi điều khoản thì hỏi lại.

ALTER TABLE user_schema.user_profiles
    ADD COLUMN IF NOT EXISTS terms_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS terms_version VARCHAR(30);
