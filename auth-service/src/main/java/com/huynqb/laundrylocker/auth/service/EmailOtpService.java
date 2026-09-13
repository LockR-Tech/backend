package com.huynqb.laundrylocker.auth.service;

import com.huynqb.laundrylocker.auth.email.EmailService;
import com.huynqb.laundrylocker.auth.model.EmailOtp;
import com.huynqb.laundrylocker.auth.repository.EmailOtpRepository;
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

    @Transactional
    public boolean sendOtp(String email, String purpose) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailOtp otp = new EmailOtp();
        otp.setEmail(normalize(email));
        otp.setPurpose(purpose);
        otp.setOtpHash(passwordEncoder.encode(code));
        otp.setExpiresAt(Instant.now().plusSeconds(300));
        repository.save(otp);
        String htmlTemplate = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <style>
                    @import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@700;900&family=Inter:wght@400;600;800&display=swap');
                    body { font-family: 'Inter', -apple-system, sans-serif; background-color: #050b14; margin: 0; padding: 0; }
                    .wrapper { width: 100%%; background-color: #050b14; padding: 40px 0; }
                    .container { max-width: 540px; margin: 0 auto; background: linear-gradient(180deg, #0a1930 0%%, #050b14 100%%); border-radius: 4px; border: 1px solid #1e3a5f; overflow: hidden; box-shadow: 0 0 30px rgba(0, 163, 255, 0.1); }
                    .header { text-align: center; padding: 40px 20px 20px; position: relative; }
                    .logo { font-family: 'Orbitron', sans-serif; font-size: 32px; font-weight: 900; color: #ffffff; letter-spacing: 4px; margin: 0; }
                    .logo span { color: #00a3ff; }
                    .subtitle { font-family: 'Orbitron', sans-serif; font-size: 11px; color: #6085b0; letter-spacing: 3px; text-transform: uppercase; margin-top: 8px; }
                    .content { padding: 30px 40px; color: #a0b9d9; text-align: center; }
                    .message { font-size: 15px; line-height: 1.6; margin-bottom: 35px; }
                    .otp-container { background: rgba(0, 163, 255, 0.05); border: 1px solid rgba(0, 163, 255, 0.2); padding: 30px; margin-bottom: 30px; position: relative; }
                    .otp-container::before { content: ''; position: absolute; top: -1px; left: -1px; width: 10px; height: 10px; border-top: 2px solid #00a3ff; border-left: 2px solid #00a3ff; }
                    .otp-container::after { content: ''; position: absolute; bottom: -1px; right: -1px; width: 10px; height: 10px; border-bottom: 2px solid #00a3ff; border-right: 2px solid #00a3ff; }
                    .otp-label { font-family: 'Orbitron', sans-serif; font-size: 10px; color: #00a3ff; letter-spacing: 2px; text-transform: uppercase; margin-bottom: 15px; }
                    .otp-code { font-family: 'Orbitron', monospace; font-size: 36px; font-weight: 700; color: #ffffff; letter-spacing: 8px; text-shadow: 0 0 15px rgba(0, 163, 255, 0.5); white-space: nowrap; }
                    .expiry { font-size: 13px; color: #f43f5e; font-weight: 600; display: inline-flex; align-items: center; justify-content: center; background: rgba(244, 63, 94, 0.1); padding: 8px 16px; border-radius: 20px; }
                    .footer { padding: 30px; text-align: center; border-top: 1px solid rgba(255, 255, 255, 0.05); background: #03070d; }
                    .footer-text { font-size: 12px; color: #4b6a8e; line-height: 1.6; margin: 0; }
                    .scanlines { position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: linear-gradient(to bottom, rgba(255,255,255,0), rgba(255,255,255,0) 50%%, rgba(0,0,0,0.1) 50%%, rgba(0,0,0,0.1)); background-size: 100%% 4px; pointer-events: none; opacity: 0.3; }
                  </style>
                </head>
                <body>
                  <div class="wrapper">
                    <div class="container">
                      <div class="header">
                        <div class="scanlines"></div>
                        <h1 class="logo">LOCK<span>.</span>R</h1>
                        <div class="subtitle">The Future of Safe Storage</div>
                      </div>
                      <div class="content">
                        <div class="message">
                          ACCESS REQUEST DETECTED.<br>
                          USE THE FOLLOWING SECURE CODE TO AUTHENTICATE YOUR IDENTITY.
                        </div>
                        <div class="otp-container">
                          <div class="otp-label">Authorization Code</div>
                          <div class="otp-code">%s</div>
                        </div>
                        <div class="expiry">
                          \u23F3 EXPIRES IN 5 MINUTES
                        </div>
                      </div>
                      <div class="footer">
                        <p class="footer-text">
                          SECURE COMMUNICATION CHANNEL &copy; 2026<br>
                          IF YOU DID NOT INITIATE THIS REQUEST, SECURE YOUR ACCOUNT IMMEDIATELY.
                        </p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(code);

        emailService.sendHtmlEmail(
                email,
                "Laundry Locker OTP",
                htmlTemplate);
        log.info("OTP generated for {} purpose {}. Development OTP: {}", email, purpose, code);
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

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
