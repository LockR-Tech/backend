package com.huynqb.laundrylocker.notification.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/// Gửi email tới một địa chỉ bất kỳ — kể cả người CHƯA có tài khoản Lock.R.
///
/// Dùng lại đúng SMTP mà auth-service đang gửi OTP đăng nhập, nên không phải ký hợp
/// đồng với nhà cung cấp mới. `JavaMailSender` lấy qua {@link ObjectProvider} để
/// service vẫn khởi động khi chưa cấu hình `spring.mail.*`.
@Slf4j
@Component
public class GuestEmailSender {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String configuredFrom;
    private final String mailUsername;
    private final String mailHost;

    public GuestEmailSender(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:}") String configuredFrom,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.host:}") String mailHost) {
        this.mailSenderProvider = mailSenderProvider;
        this.configuredFrom = configuredFrom;
        this.mailUsername = mailUsername;
        this.mailHost = mailHost;
    }

    /// `true` khi SMTP đã sẵn sàng; `false` thì nơi gọi phải coi như người nhận không
    /// nhận được email.
    ///
    /// Phải kiểm cả `spring.mail.host`: compose luôn khai báo biến này nên Spring vẫn dựng
    /// bean `JavaMailSender` kể cả khi máy chủ chưa cấu hình SMTP. Chỉ nhìn bean thì
    /// `emailChannelAvailable` báo "đã bật" trong khi chưa bật bao giờ — đúng cái mà trường
    /// này sinh ra để phân biệt.
    public boolean isReal() {
        return mailSenderProvider.getIfAvailable() != null
                && StringUtils.hasText(mailHost)
                && StringUtils.hasText(fromEmail());
    }

    public boolean send(String to, String subject, String body) {
        if (!StringUtils.hasText(to)) return false;

        if (!isReal()) {
            log.info("Email channel not configured — would have emailed {}", maskedEmail(to));
            return false;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail());
            message.setTo(to.trim());
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            return true;
        } catch (Exception ex) {
            // Hỏng kênh email không được làm hỏng đơn hàng. Không log nội dung vì
            // email chứa mã mở tủ.
            log.warn("Failed to email {}: {}", maskedEmail(to), ex.getMessage());
            return false;
        }
    }

    /// Một số nhà cung cấp (Brevo…) bắt địa chỉ gửi phải là người gửi đã xác minh,
    /// khác tài khoản SMTP — nên cho đặt riêng, mặc định lấy chính tài khoản SMTP.
    private String fromEmail() {
        return StringUtils.hasText(configuredFrom) ? configuredFrom : mailUsername;
    }

    /// `a***@example.com` — đủ để soát lỗi, không lộ địa chỉ khách.
    static String maskedEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
