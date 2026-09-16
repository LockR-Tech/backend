package com.huynqb.laundrylocker.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/// Gửi tin cho người KHÔNG có tài khoản Lock.R (người nhận hàng, người được uỷ quyền
/// lấy hộ). Khác {@code NotificationRequest} ở chỗ đích đến là số điện thoại / email
/// chứ không phải `userId`, nên không tạo bản ghi thông báo trong app.
///
/// `phone` và `email` đều tuỳ chọn nhưng phải có ít nhất một; `smsMessage` ngắn gọn cho
/// SMS, `emailBody` dài hơn cho email.
public record GuestNotificationRequest(
        String phone,
        @Email(message = "email không hợp lệ") String email,
        @NotBlank(message = "subject không được rỗng") String subject,
        @NotBlank(message = "smsMessage không được rỗng") String smsMessage,
        String emailBody) {

    /// Nội dung email; rơi về nội dung SMS khi nơi gọi không soạn riêng.
    public String emailBodyOrSms() {
        return emailBody == null || emailBody.isBlank() ? smsMessage : emailBody;
    }
}
