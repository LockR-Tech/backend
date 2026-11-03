package com.huynqb.laundrylocker.auth.service;

import com.huynqb.laundrylocker.auth.email.EmailService;
import com.huynqb.laundrylocker.auth.email.OtpEmailTemplate;
import com.huynqb.laundrylocker.auth.model.EmailOtp;
import com.huynqb.laundrylocker.auth.repository.EmailOtpRepository;
import com.huynqb.laundrylocker.auth.settings.AuthRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailOtpRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    /// Độ dài và thời hạn OTP do admin cấu hình (ADR-0005).
    private final AuthRules rules;

    @Transactional
    public boolean sendOtp(String email, String purpose) {
        int length = rules.otpLength();
        int expirySeconds = rules.otpExpirySeconds();
        String code = generateCode(length);
        EmailOtp otp = new EmailOtp();
        otp.setEmail(normalize(email));
        otp.setPurpose(purpose);
        otp.setOtpHash(passwordEncoder.encode(code));
        otp.setExpiresAt(Instant.now().plusSeconds(expirySeconds));
        repository.save(otp);
        OtpEmailTemplate.Content mail = OtpEmailTemplate.render(purpose, code, expirySeconds);
        emailService.sendHtmlEmail(email, mail.subject(), mail.html());
        // KHÔNG ghi mã OTP ra log: ai đọc được log là đăng nhập được bằng tài khoản
        // người khác (SEC-07). Chạy ở máy lập trình thì đọc mã trong hộp thư SMTP giả
        // (mailhog, mặc định http://localhost:8025), không cần log.
        log.info("OTP generated for {} purpose {}", maskEmail(email), purpose);
        return true;
    }

    @Transactional
    public boolean verifyOtp(String email, String purpose, String code) {
        return repository
                .findFirstByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(normalize(email), purpose)
                .filter(otp -> otp.getExpiresAt().isAfter(Instant.now()))
                .filter(otp -> passwordEncoder.matches(code, otp.getOtpHash()))
                .map(
                        otp -> {
                            otp.setUsed(true);
                            return true;
                        })
                .orElse(false);
    }

    /// Mã số ngẫu nhiên đúng `length` chữ số (giữ số 0 ở đầu).
    static String generateCode(int length) {
        int bound = (int) Math.pow(10, length);
        return String.format("%0" + length + "d", RANDOM.nextInt(bound));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /// `a***@example.com` — đủ để đối chiếu khi soát lỗi, không đủ để lộ email khách.
    /// Hạ chữ hoa giống `normalize` để cùng một người luôn ra cùng một chuỗi trong log,
    /// bất kể họ gõ hoa hay thường lúc đăng nhập.
    static String maskEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        int at = normalized.indexOf('@');
        if (at <= 0) return "***";
        return normalized.charAt(0) + "***" + normalized.substring(at);
    }
}
