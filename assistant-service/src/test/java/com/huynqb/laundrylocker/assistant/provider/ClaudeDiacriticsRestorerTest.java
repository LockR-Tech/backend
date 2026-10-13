package com.huynqb.laundrylocker.assistant.provider;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.assistant.config.AssistantProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/// Thêm dấu qua SDK thật, trỏ vào máy chủ HTTP giả: request nhỏ gọn, dùng đúng model đang cấu hình,
/// không thử lại; kết quả bị cắt hay bị từ chối ⇒ lỗi để bên gọi dùng câu gốc.
class ClaudeDiacriticsRestorerTest {

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile String responseBody;
    private volatile int status = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requests.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void asksTheConfiguredModelForTheAccentedSentenceOnly() throws IOException {
        responseBody = message("“Làm sao để gửi hàng cho người khác qua tủ?”", "end_turn");

        String restored = restorer("claude-haiku-4-5").restore("lam sao de gui hang cho nguoi khac qua tu?");

        assertEquals("Làm sao để gửi hàng cho người khác qua tủ?", restored);
        JsonNode request = new ObjectMapper().readTree(requestBody.get());
        assertEquals("claude-haiku-4-5", request.path("model").asText());
        assertEquals(ClaudeDiacriticsRestorer.SYSTEM_PROMPT, request.path("system").asText());
        assertTrue(request.path("system").asText().startsWith(
                "Thêm dấu tiếng Việt cho câu người dùng gửi, giữ nguyên nghĩa, mã đơn và số; chỉ trả về câu đã thêm dấu."));
        assertEquals(256 + 42, request.path("max_tokens").asInt());
        assertEquals("lam sao de gui hang cho nguoi khac qua tu?",
                request.path("messages").get(0).path("content").asText());
        assertTrue(request.path("output_config").isMissingNode(), "Haiku 4.5 does not take effort");
    }

    @Test
    void usesLowEffortOnModelsThatSupportIt() throws IOException {
        responseBody = message("Thuê ô", "end_turn");

        restorer("claude-opus-5").restore("thue o");

        assertEquals("low", new ObjectMapper().readTree(requestBody.get()).path("output_config").path("effort").asText());
    }

    @Test
    void truncatedOrRefusedResultIsAnError() {
        responseBody = message("Làm sao để", "max_tokens");
        assertThrows(ProviderException.class, () -> restorer("claude-haiku-4-5").restore("lam sao de gui hang"));

        responseBody = message("", "refusal");
        assertThrows(ProviderException.class, () -> restorer("claude-haiku-4-5").restore("lam sao de gui hang"));
    }

    @Test
    void serverErrorIsNotRetried() {
        status = 529;
        responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}";

        assertThrows(ProviderException.class, () -> restorer("claude-haiku-4-5").restore("lam sao de gui hang"));
        assertEquals(1, requests.get());
    }

    private ClaudeDiacriticsRestorer restorer(String model) {
        AssistantProperties properties = new AssistantProperties(
                "test-key", model, 2048, 30, "", "voyage-4", "http://unused", 1024, 5000);
        return new ClaudeDiacriticsRestorer(properties, new ClaudeClientProvider(properties,
                AnthropicOkHttpClient.builder()
                        .apiKey("test-key")
                        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .maxRetries(2)
                        .build()));
    }

    private static String message(String text, String stopReason) {
        String content = text.isEmpty() ? "[]" : "[{\"type\":\"text\",\"text\":\"" + text.replace("\"", "\\\"") + "\"}]";
        return "{\"id\":\"msg_r\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"m\",\"content\":" + content
                + ",\"stop_reason\":\"" + stopReason + "\",\"stop_sequence\":null,"
                + "\"usage\":{\"input_tokens\":30,\"output_tokens\":12}}";
    }
}
