package com.huynqb.laundrylocker.notification.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/// Chọn kênh gửi SMS theo cấu hình.
///
/// Có đủ ba giá trị `app.sms.twilio.account-sid`, `auth-token`, `from-number` thì dùng
/// Twilio; thiếu bất kỳ giá trị nào thì rơi về bản ghi log, để service vẫn khởi động
/// được ở máy lập trình và ở CI mà không cần khoá thật.
///
/// Kiểm tra bằng {@code StringUtils.hasText} chứ không dùng `@ConditionalOnProperty`:
/// các biến này luôn được khai báo trong `application.yml` với giá trị mặc định rỗng,
/// mà `@ConditionalOnProperty` coi chuỗi rỗng là "đã đặt" nên sẽ dựng nhầm bản Twilio
/// với khoá trống.
///
/// Khoá là **secret**, nạp qua biến môi trường trên VM — không commit vào repo.
@Slf4j
@Configuration
public class SmsChannelConfig {

    @Bean
    SmsSender smsSender(
            RestClient.Builder builder,
            @Value("${app.sms.twilio.account-sid:}") String accountSid,
            @Value("${app.sms.twilio.auth-token:}") String authToken,
            @Value("${app.sms.twilio.from-number:}") String fromNumber) {

        if (StringUtils.hasText(accountSid)
                && StringUtils.hasText(authToken)
                && StringUtils.hasText(fromNumber)) {
            log.info("SMS channel: Twilio (from {})", fromNumber);
            return new TwilioSmsSender(builder, accountSid, authToken, fromNumber);
        }

        log.warn("SMS channel not configured — pickup codes will NOT reach receivers by SMS. "
                + "Set app.sms.twilio.account-sid / auth-token / from-number to enable.");
        return new LoggingSmsSender();
    }
}
