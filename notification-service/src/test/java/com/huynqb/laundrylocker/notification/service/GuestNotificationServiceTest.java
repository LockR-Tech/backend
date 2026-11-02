package com.huynqb.laundrylocker.notification.service;

import com.huynqb.laundrylocker.notification.channel.GuestEmailSender;
import com.huynqb.laundrylocker.notification.channel.SmsSender;
import com.huynqb.laundrylocker.notification.dto.GuestNotificationRequest;
import com.huynqb.laundrylocker.notification.dto.GuestNotificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestNotificationServiceTest {

    @Mock private SmsSender smsSender;
    @Mock private GuestEmailSender emailSender;

    @InjectMocks private GuestNotificationService service;

    private static GuestNotificationRequest request(String phone, String email) {
        return new GuestNotificationRequest(
                phone, email, "Mã mở tủ Lock.R", "Ma mo tu: 482913", "Mã mở tủ: 482913");
    }

    @Test
    @DisplayName("thử cả SMS và email chứ không dừng ở kênh đầu thành công")
    void triesBothChannels() {
        when(smsSender.send(eq("0901234567"), anyString())).thenReturn(true);
        when(emailSender.send(eq("an@example.com"), anyString(), anyString(), any())).thenReturn(true);

        GuestNotificationResponse result =
                service.notifyGuest(request("0901234567", "an@example.com"));

        assertThat(result.smsSent()).isTrue();
        assertThat(result.emailSent()).isTrue();
        assertThat(result.delivered()).isTrue();
        verify(smsSender).send(eq("0901234567"), anyString());
        verify(emailSender).send(eq("an@example.com"), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("không có email thì không gọi kênh email")
    void skipsEmailWhenMissing() {
        when(smsSender.send(anyString(), anyString())).thenReturn(true);

        GuestNotificationResponse result = service.notifyGuest(request("0901234567", null));

        assertThat(result.emailSent()).isFalse();
        assertThat(result.delivered()).isTrue();
        verify(emailSender, never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("cả hai kênh trượt thì delivered() false để nơi gọi nhắc người gửi tự chuyển mã")
    void reportsFailureWhenNothingReachedReceiver() {
        when(smsSender.send(anyString(), anyString())).thenReturn(false);
        when(emailSender.send(anyString(), anyString(), anyString(), any())).thenReturn(false);

        GuestNotificationResponse result =
                service.notifyGuest(request("0901234567", "an@example.com"));

        assertThat(result.delivered()).isFalse();
    }

    @Test
    @DisplayName("người nhận dùng tiếng Anh: tiêu đề, nội dung, HTML và SMS được dịch trước khi gửi")
    void englishRecipientGetsTranslatedEmailAndSms() {
        when(smsSender.send(anyString(), anyString())).thenReturn(true);
        when(emailSender.send(anyString(), anyString(), anyString(), any(), eq(true))).thenReturn(true);

        service.notifyGuest(new GuestNotificationRequest(
                "0901234567", "an@example.com", "Mã mở tủ Lock.R cho đơn ORD-1",
                "Lock.R: Ban co kien hang ORD-1 tai Tu A, o so 4. Ma mo tu: 482913. Han lay: 14:30 03/11/2026.",
                "Xin chào An,\n\nMã đơn: ORD-1\n", "<html lang=\"vi\"><p>Xin chào An,</p></html>", "en"));

        verify(emailSender).send(eq("an@example.com"), eq("Lock.R locker code for order ORD-1"),
                eq("Hello An,\n\nOrder code: ORD-1\n"), eq("<html lang=\"en\"><p>Hello An,</p></html>"), eq(true));
        verify(smsSender).send(eq("0901234567"),
                eq("Lock.R: You have parcel ORD-1 at Tu A, box no. 4. Locker code: 482913. Pick up by: 14:30 03/11/2026."));
    }

    @Test
    @DisplayName("không ghi ngôn ngữ (nơi gọi cũ) hoặc tiếng Việt: gửi nguyên văn")
    void vietnameseOrMissingLanguageIsSentAsIs() {
        when(emailSender.send(anyString(), anyString(), anyString(), any())).thenReturn(true);

        service.notifyGuest(request(null, "an@example.com"));

        verify(emailSender).send("an@example.com", "Mã mở tủ Lock.R", "Mã mở tủ: 482913", null);
    }

    @Test
    @DisplayName("báo lại kênh nào đang chạy thật để phân biệt 'gửi hỏng' với 'chưa cấu hình'")
    void exposesChannelAvailability() {
        when(smsSender.send(anyString(), anyString())).thenReturn(false);
        when(smsSender.isReal()).thenReturn(false);
        when(emailSender.isReal()).thenReturn(true);

        GuestNotificationResponse result = service.notifyGuest(request("0901234567", null));

        assertThat(result.smsChannelAvailable()).isFalse();
        assertThat(result.emailChannelAvailable()).isTrue();
    }
}
