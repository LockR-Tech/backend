-- Ngôn ngữ app lúc đăng ký token (header X-App-Language): push gửi tới máy chọn tiếng Anh được dịch
-- tiêu đề/nội dung qua common-lib ServerText. NULL = app cũ chưa gửi ⇒ tiếng Việt.
ALTER TABLE notification_schema.fcm_tokens ADD COLUMN IF NOT EXISTS language VARCHAR(8);
