package com.huynqb.laundrylocker.notification.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/// Gửi SMS qua eSMS.vn — nhà cung cấp SMS trong nước, gửi được tới mọi nhà mạng Việt Nam.
///
/// Dùng hàm "SendMultipleMessage_V4_post_json" (mỗi request một số, một nội dung):
/// - tài liệu (VN): https://developers.esms.vn/esms-api/ham-gui-tin/tin-nhan-sms-otp-cskh
/// - tin đầu số cố định (`SmsType` 8, không cần brandname):
///   https://developers.esms.vn/en/esms-api/send-sms-api/send-fixed-number-with-cheap-price
/// - bảng mã lỗi: https://developers.esms.vn/esms-api/bang-ma-loi
/// - sandbox: https://developers.esms.vn/esms-api/moi-truong-test-sandbox
///
/// `CodeResult == "100"` chỉ nghĩa là eSMS đã NHẬN yêu cầu, chưa chắc tin đã tới máy;
/// trạng thái cuối chỉ có qua CallbackUrl — với nơi gọi thế là đủ để coi "đã gửi".
///
/// Không bao giờ ném lỗi ra ngoài; không ghi khoá, nội dung tin (chứa mã mở tủ) hay số
/// điện thoại đầy đủ vào log.
@Slf4j
public class EsmsSmsSender implements SmsSender {

    public static final String DEFAULT_BASE_URL = "https://rest.esms.vn/MainService.svc/json";
    static final String SEND_PATH = "/SendMultipleMessage_V4_post_json/";
    static final String SUCCESS_CODE = "100";
    /// Tin chăm sóc khách hàng gửi bằng brandname đã đăng ký.
    public static final String SMS_TYPE_BRANDNAME_CSKH = "2";
    /// Tin gửi bằng đầu số cố định của eSMS, không cần brandname.
    public static final String SMS_TYPE_FIXED_NUMBER = "8";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;
    private final Settings settings;

    public EsmsSmsSender(RestClient.Builder builder, Settings settings) {
        this(buildClient(builder, settings), settings);
    }

    /// Cho test: nhận sẵn `RestClient` đã gắn máy chủ giả.
    EsmsSmsSender(RestClient restClient, Settings settings) {
        this.restClient = restClient;
        this.settings = settings;
    }

    private static RestClient buildClient(RestClient.Builder builder, Settings settings) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return builder.clone()
                .baseUrl(settings.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public boolean send(String phone, String message) {
        String to = PhoneNumbers.toVietnameseNational(phone);
        if (to == null) {
            log.warn("Skip eSMS: phone {} is not a Vietnamese number", LoggingSmsSender.maskedPhone(phone));
            return false;
        }
        if (!StringUtils.hasText(message)) {
            log.warn("Skip eSMS to {}: empty content", LoggingSmsSender.maskedPhone(to));
            return false;
        }

        String body;
        try {
            body = restClient.post()
                    .uri(SEND_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody(to, message))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            log.warn("eSMS HTTP {} sending to {}", ex.getStatusCode().value(), LoggingSmsSender.maskedPhone(to));
            return false;
        } catch (Exception ex) {
            // Hết thời gian chờ, DNS, TLS… — hỏng kênh nhắn tin không được làm hỏng đơn.
            log.warn("eSMS request to {} failed: {}", LoggingSmsSender.maskedPhone(to), ex.getMessage());
            return false;
        }
        return handleResponse(to, body);
    }

    /// Thân JSON theo đúng tên trường trong tài liệu eSMS (mọi giá trị đều là chuỗi).
    Map<String, String> requestBody(String phone, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("ApiKey", settings.apiKey());
        body.put("SecretKey", settings.secretKey());
        body.put("Phone", phone);
        body.put("Content", message);
        body.put("SmsType", settings.smsType());
        if (StringUtils.hasText(settings.brandname())) {
            body.put("Brandname", settings.brandname());
        }
        body.put("IsUnicode", settings.unicode() ? "1" : "0");
        body.put("Sandbox", settings.sandbox() ? "1" : "0");
        return body;
    }

    private boolean handleResponse(String to, String body) {
        JsonNode json;
        try {
            json = StringUtils.hasText(body) ? JSON.readTree(body) : null;
        } catch (Exception ex) {
            json = null;
        }
        if (json == null || !json.isObject()) {
            log.warn("eSMS returned an unreadable response for {}", LoggingSmsSender.maskedPhone(to));
            return false;
        }

        String code = json.path("CodeResult").asText("");
        if (SUCCESS_CODE.equals(code)) {
            log.info("SMS accepted by eSMS for {} (SMSID {}{})", LoggingSmsSender.maskedPhone(to),
                    json.path("SMSID").asText("?"), settings.sandbox() ? ", sandbox — not delivered" : "");
            return true;
        }

        String error = json.hasNonNull("ErrorMessage")
                ? json.get("ErrorMessage").asText()
                : json.path("error").asText("");
        log.warn("eSMS rejected SMS to {}: CodeResult={} {} ({})", LoggingSmsSender.maskedPhone(to),
                code.isEmpty() ? "?" : code, error, describe(code));
        return false;
    }

    /// Giải thích các mã lỗi hay gặp để người vận hành biết phải làm gì mà không phải tra bảng.
    static String describe(String code) {
        return switch (code) {
            case "99" -> "phone blocked or invalid request";
            case "101" -> "wrong ApiKey/SecretKey";
            case "102" -> "no price table on the account";
            case "103" -> "insufficient balance — top up the eSMS account";
            case "104" -> "brandname not registered/active";
            case "108" -> "invalid phone number";
            case "118" -> "SmsType not supported for this account";
            case "124" -> "duplicate RequestId";
            case "140" -> "server IP not whitelisted on eSMS";
            case "143" -> "account not activated or locked";
            case "146" -> "content does not match a registered CSKH template";
            case "177" -> "carrier not registered for this brandname";
            case "201" -> "content contains a blocked keyword";
            case "300" -> "SmsType missing";
            default -> "see https://developers.esms.vn/esms-api/bang-ma-loi";
        };
    }

    /// Cấu hình eSMS đã chuẩn hoá (khoảng trắng/rỗng đã xử lý ở {@link SmsChannelConfig}).
    public record Settings(
            String apiKey,
            String secretKey,
            String brandname,
            String smsType,
            boolean unicode,
            boolean sandbox,
            String baseUrl) {

        /// Có brandname thì mặc định gửi tin CSKH bằng brandname (`2`), không có thì dùng
        /// đầu số cố định (`8`) — hai kiểu duy nhất tài liệu eSMS mô tả cho hàm này.
        public static String defaultSmsType(String brandname) {
            return StringUtils.hasText(brandname) ? SMS_TYPE_BRANDNAME_CSKH : SMS_TYPE_FIXED_NUMBER;
        }

        @Override
        public String toString() {
            // Không bao giờ in khoá ra log, kể cả khi ai đó log cả record.
            return "Settings[brandname=" + brandname + ", smsType=" + smsType + ", unicode=" + unicode
                    + ", sandbox=" + sandbox + ", baseUrl=" + baseUrl + "]";
        }
    }
}
