package com.huynqb.laundrylocker.notification.service;

import com.huynqb.laundrylocker.notification.channel.GuestEmailSender;
import com.huynqb.laundrylocker.notification.channel.SmsSender;
import com.huynqb.laundrylocker.notification.dto.GuestNotificationRequest;
import com.huynqb.laundrylocker.notification.dto.GuestNotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/// Gửi tin tới người KHÔNG có tài khoản Lock.R qua SMS và/hoặc email.
///
/// Thử CẢ HAI kênh khi có đủ thông tin, không dừng ở kênh đầu thành công: mã mở tủ
/// đến muộn hoặc không đến là hỏng cả lượt nhận hàng, nên thà gửi thừa một tin.
@Service
@RequiredArgsConstructor
public class GuestNotificationService {

    private final SmsSender smsSender;
    private final GuestEmailSender emailSender;

    public GuestNotificationResponse notifyGuest(GuestNotificationRequest request) {
        boolean smsSent = StringUtils.hasText(request.phone())
                && smsSender.send(request.phone(), request.smsMessage());

        boolean emailSent = StringUtils.hasText(request.email())
                && emailSender.send(request.email(), request.subject(), request.emailBodyOrSms());

        return new GuestNotificationResponse(
                smsSent, emailSent, smsSender.isReal(), emailSender.isReal());
    }
}
