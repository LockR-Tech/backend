package com.huynqb.laundrylocker.notification.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EsmsSmsSenderTest {

    private static final String BASE = "http://esms.test/MainService.svc/json";
    private static final String SEND_URL = BASE + "/SendMultipleMessage_V4_post_json/";
    private static final String MESSAGE = "Lock.R: Ban co kien hang ORD-1 tai Tu A, o so 3. Ma mo tu: 482913. Han lay: 09:12 14/10/2026.";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private EsmsSmsSender sender(EsmsSmsSender.Settings settings) {
        return new EsmsSmsSender(builder.build(), settings);
    }

    private static EsmsSmsSender.Settings brandname() {
        return SmsChannelConfig.esmsSettings("api-key", "secret-key", "LOCKR", "", "", "", BASE);
    }

    @Test
    @DisplayName("CodeResult 100 ⇒ gửi thành công; thân JSON đúng tên trường của eSMS, số ở dạng 0xxxxxxxxx")
    void sendsBrandnameCustomerCareMessage() {
        server.expect(once(), requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ApiKey").value("api-key"))
                .andExpect(jsonPath("$.SecretKey").value("secret-key"))
                .andExpect(jsonPath("$.Phone").value("0901234567"))
                .andExpect(jsonPath("$.Content").value(MESSAGE))
                .andExpect(jsonPath("$.SmsType").value("2"))
                .andExpect(jsonPath("$.Brandname").value("LOCKR"))
                .andExpect(jsonPath("$.IsUnicode").value("0"))
                .andExpect(jsonPath("$.Sandbox").value("0"))
                .andRespond(withSuccess("{\"CodeResult\":\"100\",\"CountRegenerate\":0,\"SMSID\":\"abc123\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(sender(brandname()).send("+84 90 123 4567", MESSAGE)).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("không có brandname ⇒ đầu số cố định (SmsType 8), không gửi trường Brandname; bật unicode/sandbox")
    void sendsFixedNumberMessageWithoutBrandname() {
        EsmsSmsSender.Settings settings =
                SmsChannelConfig.esmsSettings("k", "s", " ", null, "true", "1", BASE);
        server.expect(once(), requestTo(SEND_URL))
                .andExpect(jsonPath("$.SmsType").value("8"))
                .andExpect(jsonPath("$.Brandname").doesNotExist())
                .andExpect(jsonPath("$.IsUnicode").value("1"))
                .andExpect(jsonPath("$.Sandbox").value("1"))
                .andRespond(withSuccess("{\"CodeResult\":\"100\",\"SMSID\":\"x\"}", MediaType.APPLICATION_JSON));

        assertThat(sender(settings).send("0901234567", MESSAGE)).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("mã lỗi của eSMS (vd. 103 hết tiền) ⇒ false, không ném lỗi")
    void providerErrorCodeIsFailure() {
        server.expect(once(), requestTo(SEND_URL))
                .andRespond(withSuccess("{\"CodeResult\":\"103\",\"CountRegenerate\":0,\"ErrorMessage\":\"Balance not enough\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(sender(brandname()).send("0901234567", MESSAGE)).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("eSMS trả {\"error\": …} không có CodeResult ⇒ false")
    void errorBodyWithoutCodeIsFailure() {
        server.expect(once(), requestTo(SEND_URL))
                .andRespond(withSuccess("{\"error\":\"Invalid request\"}", MediaType.APPLICATION_JSON));

        assertThat(sender(brandname()).send("0901234567", MESSAGE)).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("thân trả về không phải JSON (trang lỗi HTML…) ⇒ false")
    void unreadableBodyIsFailure() {
        server.expect(once(), requestTo(SEND_URL))
                .andRespond(withSuccess("<html>Service Unavailable</html>", MediaType.TEXT_HTML));

        assertThat(sender(brandname()).send("0901234567", MESSAGE)).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("HTTP 5xx ⇒ false, không ném lỗi ra ngoài")
    void httpErrorIsFailure() {
        server.expect(once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThat(sender(brandname()).send("0901234567", MESSAGE)).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("hết thời gian chờ / lỗi mạng ⇒ false, không ném lỗi ra ngoài")
    void networkErrorIsFailure() {
        server.expect(once(), requestTo(SEND_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(sender(brandname()).send("0901234567", MESSAGE)).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("số nước ngoài hoặc số hỏng ⇒ không gọi eSMS")
    void skipsNumbersEsmsCannotDeliver() {
        server.expect(never(), requestTo(SEND_URL));

        EsmsSmsSender sender = sender(brandname());
        assertThat(sender.send("+14155552671", MESSAGE)).isFalse();
        assertThat(sender.send("12345", MESSAGE)).isFalse();
        assertThat(sender.send(null, MESSAGE)).isFalse();
        assertThat(sender.send("0901234567", " ")).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("toString của cấu hình không lộ khoá")
    void settingsNeverPrintKeys() {
        assertThat(brandname().toString()).doesNotContain("api-key").doesNotContain("secret-key");
    }

    @Test
    @DisplayName("mã lỗi hay gặp có lời giải thích cho người vận hành")
    void describesCommonErrorCodes() {
        assertThat(EsmsSmsSender.describe("101")).contains("ApiKey");
        assertThat(EsmsSmsSender.describe("146")).contains("template");
        assertThat(EsmsSmsSender.describe("999")).contains("bang-ma-loi");
    }
}
