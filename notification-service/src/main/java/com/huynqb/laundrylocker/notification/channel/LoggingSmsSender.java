package com.huynqb.laundrylocker.notification.channel;

import lombok.extern.slf4j.Slf4j;

/// Bản dự phòng khi chưa nạp khoá nhà cung cấp SMS: chỉ ghi log rằng lẽ ra phải gửi gì.
/// Được đăng ký trong {@link SmsChannelConfig} khi không có bean {@link SmsSender} nào khác.
///
/// KHÔNG ghi nội dung tin nhắn vào log vì tin chứa mã mở tủ — ai đọc được log là mở
/// được tủ của người khác (đúng kiểu rủi ro SEC-07 đang có với OTP email).
/// {@link #isReal()} trả `false` để nơi gọi biết người nhận sẽ không nhận được gì và
/// nhắc người gửi tự chuyển mã.
@Slf4j
public class LoggingSmsSender implements SmsSender {

    @Override
    public boolean send(String phone, String message) {
        log.info("SMS channel not configured — would have sent {} characters to {}",
                message == null ? 0 : message.length(), maskedPhone(phone));
        return false;
    }

    @Override
    public boolean isReal() {
        return false;
    }

    /// `090****567` — đủ để đối chiếu khi soát lỗi, không đủ để lộ số khách.
    static String maskedPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }
}
