package com.huynqb.laundrylocker.auth.service;

import com.huynqb.laundrylocker.auth.email.EmailService;
import com.huynqb.laundrylocker.auth.email.OtpEmailTemplate;
import com.huynqb.laundrylocker.auth.model.EmailOtp;
import com.huynqb.laundrylocker.auth.repository.EmailOtpRepository;
import com.huynqb.laundrylocker.auth.settings.TestAuthRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailOtpServiceTest {

    @Mock
    EmailOtpRepository repository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    EmailService emailService;

    @Test
    void defaultOtpHasSixDigitsAndExpiresInFiveMinutes() {
        EmailOtpService service = new EmailOtpService(repository, passwordEncoder, emailService, TestAuthRules.defaults());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        service.sendOtp("User@Mail.com", "EMAIL_LOGIN");

        assertOtp(6, Duration.ofSeconds(300), "5 phút", "Mã đăng nhập Lock.R | Lock.R");
    }

    @Test
    void otpLengthAndExpiryFollowAdminSettings() {
        EmailOtpService service = new EmailOtpService(
                repository, passwordEncoder, emailService,
                TestAuthRules.of(Map.of("app.auth.otp.length", 8, "app.auth.otp.expiry-seconds", 600)));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        service.sendOtp("user@mail.com", "PASSWORD_RESET");

        assertOtp(8, Duration.ofSeconds(600), "10 phút", "Mã đặt lại mật khẩu | Lock.R");
    }

    @Test
    void expiryTextFallsBackToSecondsWhenNotWholeMinutes() {
        assertEquals("90 giây", OtpEmailTemplate.expiryText(90));
        assertEquals("1 phút", OtpEmailTemplate.expiryText(60));
        assertEquals("1 minute", OtpEmailTemplate.expiryTextEn(60));
        assertEquals("90 seconds", OtpEmailTemplate.expiryTextEn(90));
        assertEquals(4, EmailOtpService.generateCode(4).length());
    }

    @Test
    void maskEmailKeepsEnoughToTraceButNotEnoughToIdentify() {
        assertEquals("a***@example.com", EmailOtpService.maskEmail("an@example.com"));
        assertEquals("a***@example.com", EmailOtpService.maskEmail("  An@example.com  "));
        // Chuỗi không phải email thì che sạch, không để lọt nguyên giá trị vào log.
        assertEquals("***", EmailOtpService.maskEmail(null));
        assertEquals("***", EmailOtpService.maskEmail("khong-co-a-cong"));
        assertEquals("***", EmailOtpService.maskEmail("@example.com"));
    }

    @Test
    void otpEmailIsLightInlineStyledSoDarkModeClientsKeepItReadable() {
        String html = OtpEmailTemplate.render("ADMIN_2FA", "123456", 300).html();
        assertTrue(html.contains("Mã xác thực quản trị"));
        assertTrue(html.contains("123456"));
        assertTrue(html.contains("color-scheme"));
        assertTrue(html.contains("cid:lockr-logo"), "logo Lock.R nhúng inline ở đầu thư");
        // Gmail/Outlook bỏ <style> và gradient; mẫu cũ dựa vào chúng nên mất màu ở chế độ tối.
        assertFalse(html.contains("<style"), "style must be inline");
        assertFalse(html.contains("gradient"));
        assertFalse(html.contains("rgba("));
    }

    private void assertOtp(int digits, Duration expiry, String expiryText, String subject) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(code.capture());
        assertTrue(code.getValue().matches("\\d{" + digits + "}"), code.getValue());

        ArgumentCaptor<EmailOtp> saved = ArgumentCaptor.forClass(EmailOtp.class);
        verify(repository).save(saved.capture());
        Duration remaining = Duration.between(Instant.now(), saved.getValue().getExpiresAt());
        assertTrue(remaining.compareTo(expiry) <= 0 && remaining.compareTo(expiry.minusSeconds(10)) > 0, remaining.toString());

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtmlEmail(anyString(), eq(subject), html.capture());
        assertTrue(html.getValue().contains(expiryText), "email must show configured expiry");
        assertTrue(html.getValue().contains(code.getValue()));
    }
}
