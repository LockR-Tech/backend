package com.huynqb.laundrylocker.notification.service;

import com.huynqb.laundrylocker.common.i18n.ServerText;
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
///
/// Nơi gọi soạn tiếng Việt; `language = en` ⇒ tiêu đề, nội dung, HTML và SMS được dịch qua [ServerText].
@Service
@RequiredArgsConstructor
public class GuestNotificationService {

    private final SmsSender smsSender;
    private final GuestEmailSender emailSender;

    public GuestNotificationResponse notifyGuest(GuestNotificationRequest request) {
        boolean english = ServerText.wantsEnglish(request.language());

        boolean smsSent = StringUtils.hasText(request.phone())
                && smsSender.send(request.phone(),
                        english ? ServerText.smsToEnglish(request.smsMessage()) : request.smsMessage());

        boolean emailSent = StringUtils.hasText(request.email())
                && (english
                        ? emailSender.send(request.email(), ServerText.toEnglish(request.subject()),
                                ServerText.toEnglish(request.emailBodyOrSms()),
                                ServerText.htmlToEnglish(request.emailHtml()), true)
                        : emailSender.send(
                                request.email(), request.subject(), request.emailBodyOrSms(), request.emailHtml()));

        return new GuestNotificationResponse(
                smsSent, emailSent, smsSender.isReal(), emailSender.isReal());
    }
}
