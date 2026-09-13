package com.huynqb.laundrylocker.auth.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    // Sender (From) address. Some relays (e.g. Brevo) require From to be a verified
    // sender that differs from the SMTP login, so allow configuring it independently
    // via app.mail.from; fall back to the SMTP username (fine for Gmail/SES where the
    // login is itself the sender).
    @Value("${app.mail.from:}")
    private String configuredFrom;

    @Value("${spring.mail.username:noreply@example.com}")
    private String mailUsername;

    private String fromEmail() {
        return StringUtils.hasText(configuredFrom) ? configuredFrom : mailUsername;
    }

    @Override
    @Async
    public void sendSimpleEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("Failed to send simple email to {}: {}", to, ex.getMessage());
        }
    }

    @Override
    @Async
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException ex) {
            log.warn("Failed to send HTML email to {}: {}", to, ex.getMessage());
        } catch (Exception ex) {
            log.warn("Unexpected email error for {}: {}", to, ex.getMessage());
        }
    }
}
