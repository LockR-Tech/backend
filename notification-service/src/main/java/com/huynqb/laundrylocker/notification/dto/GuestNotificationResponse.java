package com.huynqb.laundrylocker.notification.dto;

/// Kết quả gửi tin cho người không có tài khoản.
///
/// Nơi gọi cần biết CÓ THẬT SỰ gửi được không để nói đúng với người gửi hàng: khi cả
/// hai kênh đều trượt, app phải nhắc người gửi tự chuyển mã thay vì để họ tưởng người
/// nhận đã nhận được.
public record GuestNotificationResponse(
        boolean smsSent,
        boolean emailSent,
        /// `true` khi kênh SMS đang chạy thật (không phải bản ghi log).
        boolean smsChannelAvailable,
        /// `true` khi SMTP đã cấu hình.
        boolean emailChannelAvailable) {

    public boolean delivered() {
        return smsSent || emailSent;
    }
}
