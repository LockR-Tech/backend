package com.huynqb.laundrylocker.notification.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class SmsChannelConfigTest {

    private static final SmsChannelConfig.TwilioKeys TWILIO =
            new SmsChannelConfig.TwilioKeys("AC123", "token", "+15005550006");
    private static final SmsChannelConfig.TwilioKeys NO_TWILIO = new SmsChannelConfig.TwilioKeys("", "", "");
    private static final EsmsSmsSender.Settings ESMS =
            SmsChannelConfig.esmsSettings("key", "secret", "", "", "", "", "");
    private static final EsmsSmsSender.Settings NO_ESMS =
            SmsChannelConfig.esmsSettings("", "", "", "", "", "", "");

    private static SmsSender select(String provider, SmsChannelConfig.TwilioKeys twilio, EsmsSmsSender.Settings esms) {
        return SmsChannelConfig.select(RestClient.builder(), provider, twilio, esms);
    }

    @Test
    @DisplayName("provider trống: eSMS được ưu tiên khi đủ khoá")
    void autoPrefersEsms() {
        assertThat(select("", TWILIO, ESMS)).isInstanceOf(EsmsSmsSender.class);
        assertThat(select(null, NO_TWILIO, ESMS)).isInstanceOf(EsmsSmsSender.class);
    }

    @Test
    @DisplayName("provider trống, không có eSMS: Twilio vẫn chạy như trước")
    void autoFallsBackToTwilio() {
        assertThat(select("", TWILIO, NO_ESMS)).isInstanceOf(TwilioSmsSender.class);
    }

    @Test
    @DisplayName("provider trống, không có khoá nào: chỉ ghi log và báo kênh chưa bật")
    void autoWithoutKeysLogsOnly() {
        SmsSender sender = select("  ", NO_TWILIO, NO_ESMS);
        assertThat(sender).isInstanceOf(LoggingSmsSender.class);
        assertThat(sender.isReal()).isFalse();
    }

    @Test
    @DisplayName("chọn rõ nhà cung cấp thì dùng đúng nhà đó, kể cả khi nhà kia cũng có khoá")
    void explicitProviderWins() {
        assertThat(select("twilio", TWILIO, ESMS)).isInstanceOf(TwilioSmsSender.class);
        assertThat(select(" ESMS ", TWILIO, ESMS)).isInstanceOf(EsmsSmsSender.class);
    }

    @Test
    @DisplayName("chọn nhà cung cấp mà thiếu khoá ⇒ ghi log, KHÔNG lặng lẽ đổi sang nhà khác")
    void explicitProviderWithoutKeysLogsOnly() {
        assertThat(select("esms", TWILIO, NO_ESMS)).isInstanceOf(LoggingSmsSender.class);
        assertThat(select("twilio", NO_TWILIO, ESMS)).isInstanceOf(LoggingSmsSender.class);
    }

    @Test
    @DisplayName("log/none hoặc tên lạ ⇒ chỉ ghi log")
    void disabledOrUnknownProviderLogsOnly() {
        assertThat(select("log", TWILIO, ESMS)).isInstanceOf(LoggingSmsSender.class);
        assertThat(select("none", TWILIO, ESMS)).isInstanceOf(LoggingSmsSender.class);
        assertThat(select("viettel", TWILIO, ESMS)).isInstanceOf(LoggingSmsSender.class);
    }

    @Test
    @DisplayName("giá trị trống từ compose rơi về mặc định: đầu số cố định, không dấu, không sandbox, host eSMS")
    void blankEsmsOptionsUseDefaults() {
        EsmsSmsSender.Settings settings = SmsChannelConfig.esmsSettings(" key ", " secret ", "", "", "", "", "");

        assertThat(settings.apiKey()).isEqualTo("key");
        assertThat(settings.secretKey()).isEqualTo("secret");
        assertThat(settings.smsType()).isEqualTo(EsmsSmsSender.SMS_TYPE_FIXED_NUMBER);
        assertThat(settings.unicode()).isFalse();
        assertThat(settings.sandbox()).isFalse();
        assertThat(settings.baseUrl()).isEqualTo(EsmsSmsSender.DEFAULT_BASE_URL);
    }

    @Test
    @DisplayName("có brandname ⇒ mặc định CSKH (SmsType 2); đặt sms-type thì theo cấu hình")
    void smsTypeFollowsBrandnameUnlessConfigured() {
        assertThat(SmsChannelConfig.esmsSettings("k", "s", "LOCKR", "", "", "", "").smsType())
                .isEqualTo(EsmsSmsSender.SMS_TYPE_BRANDNAME_CSKH);
        assertThat(SmsChannelConfig.esmsSettings("k", "s", "LOCKR", " 8 ", "", "", "").smsType())
                .isEqualTo("8");
    }

    @Test
    @DisplayName("cờ bật/tắt nhận true/1/yes, còn lại là tắt")
    void parsesFlags() {
        assertThat(SmsChannelConfig.flag("true")).isTrue();
        assertThat(SmsChannelConfig.flag("TRUE")).isTrue();
        assertThat(SmsChannelConfig.flag("1")).isTrue();
        assertThat(SmsChannelConfig.flag("yes")).isTrue();
        assertThat(SmsChannelConfig.flag("false")).isFalse();
        assertThat(SmsChannelConfig.flag("")).isFalse();
        assertThat(SmsChannelConfig.flag(null)).isFalse();
    }
}
