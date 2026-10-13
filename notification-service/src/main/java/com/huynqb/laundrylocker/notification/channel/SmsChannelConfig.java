package com.huynqb.laundrylocker.notification.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Locale;

/// Chọn kênh gửi SMS theo cấu hình.
///
/// `app.sms.provider`:
/// - `esms` — eSMS.vn (cần `app.sms.esms.api-key` + `secret-key`);
/// - `twilio` — Twilio (cần `app.sms.twilio.account-sid` + `auth-token` + `from-number`);
/// - `log` / `none` — chỉ ghi log;
/// - trống — tự chọn: eSMS nếu đủ khoá, rồi tới Twilio, không có gì thì chỉ ghi log.
///
/// Chọn nhà cung cấp mà thiếu khoá, hoặc ghi sai tên nhà cung cấp, thì rơi về bản ghi log
/// kèm cảnh báo rõ ràng — service vẫn khởi động được ở máy lập trình và ở CI.
///
/// Kiểm tra bằng {@code StringUtils.hasText} chứ không dùng `@ConditionalOnProperty`:
/// các biến này luôn được khai báo trong `application.yml` với giá trị mặc định rỗng,
/// mà `@ConditionalOnProperty` coi chuỗi rỗng là "đã đặt" nên sẽ dựng nhầm bản thật
/// với khoá trống. Vì compose truyền biến rỗng (đè mặc định của Spring), các giá trị
/// tuỳ chọn cũng được đọc dạng chuỗi và tự xử lý khi trống.
///
/// Khoá là **secret**, nạp qua biến môi trường trên VM — không commit vào repo.
@Slf4j
@Configuration
public class SmsChannelConfig {

    @Bean
    SmsSender smsSender(
            RestClient.Builder builder,
            @Value("${app.sms.provider:}") String provider,
            @Value("${app.sms.twilio.account-sid:}") String accountSid,
            @Value("${app.sms.twilio.auth-token:}") String authToken,
            @Value("${app.sms.twilio.from-number:}") String fromNumber,
            @Value("${app.sms.esms.api-key:}") String esmsApiKey,
            @Value("${app.sms.esms.secret-key:}") String esmsSecretKey,
            @Value("${app.sms.esms.brandname:}") String esmsBrandname,
            @Value("${app.sms.esms.sms-type:}") String esmsSmsType,
            @Value("${app.sms.esms.unicode:}") String esmsUnicode,
            @Value("${app.sms.esms.sandbox:}") String esmsSandbox,
            @Value("${app.sms.esms.base-url:}") String esmsBaseUrl) {

        TwilioKeys twilio = new TwilioKeys(accountSid, authToken, fromNumber);
        EsmsSmsSender.Settings esms = esmsSettings(
                esmsApiKey, esmsSecretKey, esmsBrandname, esmsSmsType, esmsUnicode, esmsSandbox, esmsBaseUrl);
        return select(builder, provider, twilio, esms);
    }

    static SmsSender select(RestClient.Builder builder, String provider, TwilioKeys twilio,
                            EsmsSmsSender.Settings esms) {
        String choice = StringUtils.hasText(provider) ? provider.trim().toLowerCase(Locale.ROOT) : "";
        boolean esmsReady = StringUtils.hasText(esms.apiKey()) && StringUtils.hasText(esms.secretKey());

        switch (choice) {
            case "esms" -> {
                if (esmsReady) return esms(builder, esms);
                log.warn("SMS channel not configured — app.sms.provider=esms but "
                        + "app.sms.esms.api-key / secret-key are missing. Pickup codes will NOT go out by SMS.");
                return new LoggingSmsSender();
            }
            case "twilio" -> {
                if (twilio.ready()) return twilio(builder, twilio);
                log.warn("SMS channel not configured — app.sms.provider=twilio but "
                        + "app.sms.twilio.account-sid / auth-token / from-number are missing. "
                        + "Pickup codes will NOT go out by SMS.");
                return new LoggingSmsSender();
            }
            case "log", "none" -> {
                log.warn("SMS channel disabled (app.sms.provider={}) — pickup codes will NOT go out by SMS.", choice);
                return new LoggingSmsSender();
            }
            case "" -> {
                if (esmsReady) return esms(builder, esms);
                if (twilio.ready()) return twilio(builder, twilio);
                log.warn("SMS channel not configured — pickup codes will NOT reach receivers by SMS. "
                        + "Set app.sms.esms.api-key / secret-key (eSMS) or app.sms.twilio.* (Twilio) to enable.");
                return new LoggingSmsSender();
            }
            default -> {
                log.warn("SMS channel not configured — unknown app.sms.provider '{}' (use esms, twilio or leave empty). "
                        + "Pickup codes will NOT go out by SMS.", choice);
                return new LoggingSmsSender();
            }
        }
    }

    private static SmsSender esms(RestClient.Builder builder, EsmsSmsSender.Settings settings) {
        log.info("SMS channel: eSMS ({}, SmsType {}{}{})",
                StringUtils.hasText(settings.brandname()) ? "brandname " + settings.brandname() : "fixed number",
                settings.smsType(),
                settings.unicode() ? ", unicode" : "",
                settings.sandbox() ? ", SANDBOX — messages are NOT delivered" : "");
        return new EsmsSmsSender(builder, settings);
    }

    private static SmsSender twilio(RestClient.Builder builder, TwilioKeys keys) {
        log.info("SMS channel: Twilio (from {})", keys.fromNumber());
        return new TwilioSmsSender(builder, keys.accountSid(), keys.authToken(), keys.fromNumber());
    }

    static EsmsSmsSender.Settings esmsSettings(String apiKey, String secretKey, String brandname, String smsType,
                                               String unicode, String sandbox, String baseUrl) {
        String brand = trimToEmpty(brandname);
        return new EsmsSmsSender.Settings(
                trimToEmpty(apiKey),
                trimToEmpty(secretKey),
                brand,
                StringUtils.hasText(smsType) ? smsType.trim() : EsmsSmsSender.Settings.defaultSmsType(brand),
                flag(unicode),
                flag(sandbox),
                StringUtils.hasText(baseUrl) ? baseUrl.trim() : EsmsSmsSender.DEFAULT_BASE_URL);
    }

    /// `true`/`1`/`yes` (không phân biệt hoa thường) ⇒ bật; trống hoặc giá trị khác ⇒ tắt.
    static boolean flag(String value) {
        if (!StringUtils.hasText(value)) return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("1") || v.equals("yes");
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    record TwilioKeys(String accountSid, String authToken, String fromNumber) {
        boolean ready() {
            return StringUtils.hasText(accountSid) && StringUtils.hasText(authToken) && StringUtils.hasText(fromNumber);
        }

        @Override
        public String toString() {
            return "TwilioKeys[fromNumber=" + fromNumber + "]";
        }
    }
}
