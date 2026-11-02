-- Ngôn ngữ app người dùng chọn (vi/en) — order-service soạn thư/SMS gửi ngoài app theo cột này (ADR-0010).
-- NULL = app chưa báo ⇒ tiếng Việt.
ALTER TABLE user_schema.user_profiles ADD COLUMN IF NOT EXISTS language VARCHAR(8);
