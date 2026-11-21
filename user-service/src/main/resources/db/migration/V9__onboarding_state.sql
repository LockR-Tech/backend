-- Hướng dẫn lần đầu trên màn hình (onboarding tour, F3-G13) — trạng thái theo từng người dùng, dùng chung
-- cho web admin và app mobile: xem trên thiết bị nào cũng tính là đã xem.
-- auto_show:   FALSE = người dùng chọn "Bỏ qua vĩnh viễn" ⇒ không tự mở nữa (vẫn mở tay được).
-- seen_groups: {"mã nhóm": phiên bản đã xem}; nhóm tăng phiên bản ⇒ app báo "Có hướng dẫn mới".
-- "Bỏ qua" một lần chỉ lưu ở phía máy khách (theo phiên), không ghi vào đây.

CREATE TABLE IF NOT EXISTS user_schema.user_onboarding_state (
    user_id     BIGINT PRIMARY KEY,
    auto_show   BOOLEAN   NOT NULL DEFAULT TRUE,
    seen_groups JSONB     NOT NULL DEFAULT '{}'::jsonb,
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);
