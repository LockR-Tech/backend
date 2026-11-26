-- Nhật ký thao tác (F3-G16): ai, lúc nào, làm gì, với đối tượng nào — bật bảo trì, kích hoạt tủ, gán KTV, đổi
-- trạng thái ô / drone, khoá tài khoản, đổi vai trò, xoá… Các service gửi sự kiện `admin.action.logged`, user-service
-- lưu vào đây và trả cho web admin (`/api/admin/audit-logs`). Chỉ thêm, không sửa / xoá dòng cũ.
--
-- actor_user_id NULL = hệ thống tự làm (job hẹn giờ, thiết bị). actor_name chụp tên lúc ghi để nhật ký vẫn đọc
-- được khi tài khoản đổi tên hay bị xoá. target_label: mã tủ / số ô / mã drone / email lúc ghi.
CREATE TABLE IF NOT EXISTS user_schema.action_logs (
    id            BIGSERIAL PRIMARY KEY,
    event_id      VARCHAR(64) UNIQUE,
    occurred_at   TIMESTAMP     NOT NULL,
    source        VARCHAR(60),
    actor_user_id BIGINT,
    actor_name    VARCHAR(255),
    actor_roles   VARCHAR(255),
    action        VARCHAR(80)   NOT NULL,
    target_type   VARCHAR(40),
    target_id     BIGINT,
    target_label  VARCHAR(255),
    locker_id     BIGINT,
    from_value    VARCHAR(500),
    to_value      VARCHAR(500),
    note          VARCHAR(2000)
);

CREATE INDEX IF NOT EXISTS idx_action_logs_occurred ON user_schema.action_logs (occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_action_logs_target ON user_schema.action_logs (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_action_logs_locker ON user_schema.action_logs (locker_id);
CREATE INDEX IF NOT EXISTS idx_action_logs_actor ON user_schema.action_logs (actor_user_id);
