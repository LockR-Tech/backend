package com.huynqb.laundrylocker.notification.dto;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/// Body của POST /api/admin/notifications/broadcast.
public record AdminBroadcastRequest(
        @NotBlank String title,
        @NotBlank String message,
        String type,
        Long referenceId,
        String referenceType,
        /// Không gửi (null) ⇒ gửi mọi người dùng đã đăng ký FCM token như trước.
        /// Có gửi ⇒ chỉ tạo/gửi cho đúng các user này (kể cả user chưa có FCM token,
        /// để tin vẫn nằm trong hộp thư); danh sách rỗng ⇒ không gửi cho ai.
        List<Long> userIds) {

    /// Nội dung chung cho từng người nhận (userId điền khi gửi).
    public NotificationRequest template() {
        return new NotificationRequest(null, title, message, type, referenceId, referenceType);
    }
}
