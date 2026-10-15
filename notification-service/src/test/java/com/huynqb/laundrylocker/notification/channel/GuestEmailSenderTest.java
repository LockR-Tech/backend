package com.huynqb.laundrylocker.notification.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Multipart;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// `emailChannelAvailable` sinh ra để phân biệt "gửi hỏng" với "chưa bật bao giờ", nên
/// [GuestEmailSender#isReal()] không được chỉ nhìn bean `JavaMailSender`: compose luôn khai
/// báo `spring.mail.*` nên Spring vẫn dựng bean kể cả khi máy chủ chưa cấu hình SMTP.
@ExtendWith(MockitoExtension.class)
class GuestEmailSenderTest {

    @Mock private ObjectProvider<JavaMailSender> provider;
    @Mock private JavaMailSender mailSender;

    private GuestEmailSender sender(String from, String username, String host) {
        return new GuestEmailSender(provider, from, username, host);
    }

    @Test
    @DisplayName("có bean nhưng chưa đặt spring.mail.host ⇒ kênh chưa cấu hình")
    void beanWithoutHostIsNotReal() {
        when(provider.getIfAvailable()).thenReturn(mailSender);

        assertThat(sender("", "noreply@laundry.test", "").isReal()).isFalse();
    }

    @Test
    @DisplayName("có host nhưng không có địa chỉ gửi ⇒ kênh chưa cấu hình")
    void hostWithoutFromAddressIsNotReal() {
        when(provider.getIfAvailable()).thenReturn(mailSender);

        assertThat(sender("", "", "smtp.brevo.com").isReal()).isFalse();
    }

    @Test
    @DisplayName("không có bean ⇒ kênh chưa cấu hình")
    void missingBeanIsNotReal() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThat(sender("no-reply@lockr.vn", "login@brevo", "smtp.brevo.com").isReal()).isFalse();
    }

    @Test
    @DisplayName("đủ bean + host + địa chỉ gửi ⇒ kênh đã bật")
    void fullyConfiguredIsReal() {
        when(provider.getIfAvailable()).thenReturn(mailSender);

        assertThat(sender("no-reply@lockr.vn", "login@brevo", "smtp.brevo.com").isReal()).isTrue();
    }

    @Test
    @DisplayName("app.mail.from trống thì lấy spring.mail.username làm địa chỉ gửi")
    void fallsBackToSmtpUsernameAsSender() {
        when(provider.getIfAvailable()).thenReturn(mailSender);

        assertThat(sender("", "no-reply@lockr.vn", "smtp.gmail.com").isReal()).isTrue();
    }

    @Test
    @DisplayName("chưa cấu hình thì không gọi SMTP, trả false để nơi gọi nhắc người gửi tự chuyển mã")
    void doesNotSendWhenChannelIsNotConfigured() {
        when(provider.getIfAvailable()).thenReturn(mailSender);

        boolean sent = sender("", "noreply@laundry.test", "")
                .send("an@example.com", "Mã mở tủ Lock.R", "Mã mở tủ: 482913");

        assertThat(sent).isFalse();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("gửi email có bản text, HTML Lock.R và logo nhúng CID")
    void sendsBrandedHtmlWithInlineLogo() throws Exception {
        when(provider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        boolean sent = sender("no-reply@lockr.vn", "login@lockr.vn", "smtp.gmail.com")
                .send("an@example.com", "Đơn hàng Lock.R", "Nội dung dự phòng");

        assertThat(sent).isTrue();
        var messageCaptor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent()).isInstanceOf(Multipart.class);
        String parts = multipartText(messageCaptor.getValue());
        assertThat(parts).contains("Nội dung dự phòng", "Lock.R", "cid:lockr-logo", "Đơn hàng Lock.R");
    }

    @Test
    @DisplayName("dùng template HTML riêng khi bên gọi truyền emailHtml")
    void usesProvidedHtmlForDroneEmail() throws Exception {
        when(provider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        boolean sent = sender("no-reply@lockr.vn", "login@lockr.vn", "smtp.gmail.com")
                .send("an@example.com", "Đơn drone", "Nội dung text", "<html><body>DRONE_TEMPLATE</body></html>");

        assertThat(sent).isTrue();
        var messageCaptor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(multipartText(messageCaptor.getValue())).contains("DRONE_TEMPLATE");
    }

    @Test
    @DisplayName("địa chỉ người nhận trống thì bỏ qua")
    void skipsBlankRecipient() {
        assertThat(sender("no-reply@lockr.vn", "login", "smtp.brevo.com").send("", "s", "b")).isFalse();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("địa chỉ trong log được che bớt vì email chứa mã mở tủ")
    void masksEmailForLogs() {
        assertThat(GuestEmailSender.maskedEmail("an@example.com")).isEqualTo("a***@example.com");
        assertThat(GuestEmailSender.maskedEmail("@example.com")).isEqualTo("***");
        assertThat(GuestEmailSender.maskedEmail(null)).isEqualTo("***");
    }

    private static String multipartText(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) return text;
        if (content instanceof Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                text.append(multipartText(multipart.getBodyPart(i)));
            }
            return text.toString();
        }
        return "";
    }
}
