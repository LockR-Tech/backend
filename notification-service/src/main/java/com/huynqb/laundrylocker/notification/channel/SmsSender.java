package com.huynqb.laundrylocker.notification.channel;

/// Gửi SMS tới một số điện thoại bất kỳ — kể cả người CHƯA có tài khoản Lock.R.
///
/// Tách thành giao diện để đổi nhà cung cấp mà không chạm nghiệp vụ: bản chạy thật
/// hiện tại là Twilio ({@link TwilioSmsSender}), bản dự phòng chỉ ghi log
/// ({@link LoggingSmsSender}) được dùng khi chưa nạp khoá.
///
/// Mọi bản cài đặt đều KHÔNG được ném lỗi ra ngoài: gửi tin nhắn hỏng thì đơn hàng
/// vẫn phải chạy tiếp, chỉ mất một kênh thông báo.
public interface SmsSender {

    /// @param phone   số điện thoại người nhận, dạng người dùng nhập (`0901234567`)
    ///                hoặc E.164 (`+84901234567`)
    /// @param message nội dung, đã sẵn sàng gửi
    /// @return `true` khi nhà cung cấp đã nhận tin để gửi
    boolean send(String phone, String message);

    /// `false` khi đây chỉ là bản ghi log — dùng để báo lên nơi gọi rằng người nhận
    /// sẽ KHÔNG thực sự nhận được gì.
    default boolean isReal() {
        return true;
    }
}
