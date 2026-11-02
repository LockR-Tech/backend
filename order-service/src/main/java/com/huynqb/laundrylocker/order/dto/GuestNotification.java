package com.huynqb.laundrylocker.order.dto;

/// Hợp đồng gọi `POST /internal/notifications/guest` của notification-service để gửi
/// tin cho người KHÔNG có tài khoản Lock.R.
public final class GuestNotification {

    private GuestNotification() {
    }

    /// `phone` và `email` đều tuỳ chọn nhưng phải có ít nhất một. Nội dung soạn tiếng Việt; `language = en` ⇒
    /// notification-service dịch trước khi gửi (ADR-0010).
    public record Request(
            String phone, String email, String subject, String smsMessage, String emailBody, String emailHtml,
            String language) {

        public Request(String phone, String email, String subject, String smsMessage, String emailBody) {
            this(phone, email, subject, smsMessage, emailBody, null, null);
        }

        public Request(
                String phone, String email, String subject, String smsMessage, String emailBody, String emailHtml) {
            this(phone, email, subject, smsMessage, emailBody, emailHtml, null);
        }

        public Request inLanguage(String language) {
            return new Request(phone, email, subject, smsMessage, emailBody, emailHtml, language);
        }
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
