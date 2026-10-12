-- Tài khoản đã có mật khẩu do người dùng tự đặt hay chỉ có mật khẩu ngẫu nhiên hệ thống tạo
-- (đăng nhập Google/Firebase, đăng ký OTP không nhập mật khẩu). App dựa vào đây để hỏi
-- "mật khẩu hiện tại" khi đổi mật khẩu, hoặc cho đặt mật khẩu đầu tiên.
ALTER TABLE auth_schema.auth_accounts
    ADD COLUMN IF NOT EXISTS password_set BOOLEAN NOT NULL DEFAULT TRUE;

-- Dữ liệu cũ: tài khoản được tạo ngay trong lần đăng nhập mạng xã hội đầu tiên (liên kết social
-- tạo cùng lúc với tài khoản) hoặc có auth_provider khác LOCAL chưa từng có mật khẩu tự đặt.
-- Tài khoản có mật khẩu rồi mới liên kết Google sau đó vẫn giữ TRUE. Tài khoản đăng ký OTP cũ
-- không phân biệt được nên giữ TRUE (vẫn đặt lại được qua "Quên mật khẩu").
UPDATE auth_schema.auth_accounts a
SET password_set = FALSE
WHERE a.auth_provider <> 'LOCAL'
   OR EXISTS (SELECT 1
              FROM auth_schema.social_identities s
              WHERE s.account_id = a.id
                AND s.created_at BETWEEN a.created_at - INTERVAL '5 minutes' AND a.created_at + INTERVAL '5 minutes');
