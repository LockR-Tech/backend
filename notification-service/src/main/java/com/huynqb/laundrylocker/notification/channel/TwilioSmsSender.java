package com.huynqb.laundrylocker.notification.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/// Gửi SMS qua Twilio Messages API (`POST /2010-04-01/Accounts/{sid}/Messages.json`).
///
/// Dùng `RestClient` gọi thẳng HTTP thay vì SDK Twilio: chỉ cần đúng một endpoint,
/// thêm một thư viện nữa không đáng.
///
/// Lưu ý về tài khoản dùng thử: Twilio trial CHỈ gửi được tới số đã xác minh trong
/// console, và tin nhắn bị chèn thêm dòng quảng cáo. Nâng lên tài khoản trả phí là
/// gửi được mọi số, không phải sửa dòng code nào.
@Slf4j
public class TwilioSmsSender implements SmsSender {

    private static final String API_ROOT = "https://api.twilio.com/2010-04-01";

    private final RestClient restClient;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    public TwilioSmsSender(RestClient.Builder builder, String accountSid, String authToken, String fromNumber) {
        this.restClient = builder.baseUrl(API_ROOT).build();
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    @Override
    public boolean send(String phone, String message) {
        String to = PhoneNumbers.toE164(phone);
        if (to == null) {
            log.warn("Skip SMS: cannot parse phone number");
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", to);
        form.add("From", fromNumber);
        form.add("Body", message);

        try {
            restClient.post()
                    .uri("/Accounts/{sid}/Messages.json", accountSid)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            // Không ném ra ngoài: hỏng kênh nhắn tin không được làm hỏng đơn hàng.
            // Không log nội dung tin vì tin chứa mã mở tủ.
            log.warn("Failed to send SMS to {}: {}", LoggingSmsSender.maskedPhone(to), ex.getMessage());
            return false;
        }
    }

    private String basicAuth() {
        String raw = accountSid + ":" + authToken;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
