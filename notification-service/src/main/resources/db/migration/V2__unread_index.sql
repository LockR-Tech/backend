-- Tối ưu DB: index phục vụ đếm + liệt kê thông báo CHƯA ĐỌC (gọi mỗi lần mở
-- app). Đối chiếu NotificationRepository.countByUserIdAndIsReadFalse và
-- findByUserIdAndIsReadFalseOrderByCreatedAtDesc. Composite (user_id, is_read,
-- created_at DESC) phục vụ trọn vẹn cả filter lẫn sort; idx_notifications_user_id
-- cũ vẫn giữ cho truy vấn "tất cả thông báo của user".
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON notification_schema.notifications(user_id, is_read, created_at DESC);
