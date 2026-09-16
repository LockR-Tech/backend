package com.huynqb.laundrylocker.order.dto;

/// Hợp đồng gọi `POST /internal/notifications/guest` của notification-service để gửi
/// tin cho người KHÔNG có tài khoản Lock.R.
public final class GuestNotification {

    private GuestNotification() {
    }

    /// `phone` và `email` đều tuỳ chọn nhưng phải có ít nhất một.
    public record Request(
            String phone, String email, String subject, String smsMessage, String emailBody) {
    }

    /// Nơi gọi cần biết CÓ thật sự gửi được không để nói đúng với người gửi hàng.
    public record Result(
            boolean smsSent,
            boolean emailSent,
            boolean smsChannelAvailable,
            boolean emailChannelAvailable) {

        public boolean delivered() {
            return smsSent || emailSent;
        }
    }
}
