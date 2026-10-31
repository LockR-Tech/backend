-- Xoá người dùng ở trang admin là xoá mềm: hồ sơ vẫn còn trong DB kèm lý do, thời điểm và người xoá.
ALTER TABLE user_schema.user_profiles
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN deleted_by BIGINT,
    ADD COLUMN deleted_reason VARCHAR(500);

-- Email/số điện thoại của người đã xoá được dùng lại khi đăng ký hoặc tạo tài khoản mới.
ALTER TABLE user_schema.user_profiles DROP CONSTRAINT user_profiles_email_key;
ALTER TABLE user_schema.user_profiles DROP CONSTRAINT user_profiles_phone_number_key;
CREATE UNIQUE INDEX uq_user_profiles_email_not_deleted
    ON user_schema.user_profiles (email)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_user_profiles_phone_not_deleted
    ON user_schema.user_profiles (phone_number)
    WHERE deleted_at IS NULL;
