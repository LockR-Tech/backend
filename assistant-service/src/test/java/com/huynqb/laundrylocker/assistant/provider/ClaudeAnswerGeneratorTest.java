package com.huynqb.laundrylocker.assistant.provider;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.assistant.config.AssistantProperties;
import com.huynqb.laundrylocker.assistant.knowledge.RetrievedChunk;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.ChatTurn;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.GeneratedAnswer;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/// Gọi Claude qua SDK thật, trỏ vào máy chủ HTTP giả: kiểm định dạng request (document block bật
/// citations) và cách đọc trích dẫn / từ chối — không tốn phí API.
class ClaudeAnswerGeneratorTest {

    private static final AssistantProperties PROPERTIES = new AssistantProperties(
            "test-key", "claude-opus-5", 2048, 30, "", "voyage-4", "http://unused", 1024, 5000);

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile String responseBody;
    private ClaudeAnswerGenerator generator;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        generator = new ClaudeAnswerGenerator(PROPERTIES, AnthropicOkHttpClient.builder()
                .apiKey("test-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .maxRetries(0)
                .build());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsChunksAsCitableDocumentsAndMapsCitationsBack() throws IOException {
        responseBody = """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5",
                 "content":[
                   {"type":"text","text":"Bạn được hoàn tiền "},
                   {"type":"text","text":"trong 7 ngày.","citations":[{"type":"char_location",
                     "cited_text":"Hoàn tiền trong 7 ngày.","document_index":1,"document_title":"Chính sách hoàn tiền",
                     "start_char_index":0,"end_char_index":23}]}],
                 "stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":321,"output_tokens":17}}
                """;
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk(11, 1, "Điều khoản", 0, "Phí", "Phí gửi 15.000đ.", 0.5),
                new RetrievedChunk(12, 2, "Chính sách hoàn tiền", 0, null, "Hoàn tiền trong 7 ngày.", 0.7));
        List<ChatTurn> history = List.of(
                new ChatTurn(true, "Phí gửi bao nhiêu?"), new ChatTurn(false, "15.000đ mỗi đơn."));

        GeneratedAnswer answer = generator.generate("Còn hoàn tiền thì sao?", chunks, history);

        assertEquals("Bạn được hoàn tiền trong 7 ngày.", answer.text());
        assertFalse(answer.refused());
        assertEquals(List.of(new AnswerGenerator.Citation(1, "Hoàn tiền trong 7 ngày.")), answer.citations());
        assertEquals(321, answer.inputTokens());

        JsonNode request = new ObjectMapper().readTree(requestBody.get());
        assertEquals("claude-opus-5", request.path("model").asText());
        assertEquals("low", request.path("output_config").path("effort").asText());
        assertTrue(request.path("system").asText().contains("Chỉ trả lời dựa trên các tài liệu"));
        JsonNode messages = request.path("messages");
        assertEquals(3, messages.size());
        assertEquals("user", messages.get(0).path("role").asText());
        assertEquals("assistant", messages.get(1).path("role").asText());
        JsonNode blocks = messages.get(2).path("content");
        assertEquals(3, blocks.size());
        JsonNode document = blocks.get(0);
        assertEquals("document", document.path("type").asText());
        assertEquals("text", document.path("source").path("type").asText());
        assertEquals("text/plain", document.path("source").path("media_type").asText());
        assertEquals("Phí gửi 15.000đ.", document.path("source").path("data").asText());
        assertEquals("Điều khoản", document.path("title").asText());
        assertEquals("Mục: Phí", document.path("context").asText());
        assertTrue(document.path("citations").path("enabled").asBoolean());
        assertTrue(blocks.get(1).path("context").isMissingNode());
        assertEquals("text", blocks.get(2).path("type").asText());
        assertEquals("Còn hoàn tiền thì sao?", blocks.get(2).path("text").asText());
    }

    @Test
    void refusalStopReasonBecomesAPoliteRefusal() {
        responseBody = """
                {"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5","content":[],
                 "stop_reason":"refusal","stop_sequence":null,"usage":{"input_tokens":50,"output_tokens":0}}
                """;

        GeneratedAnswer answer = generator.generate(
                "?", List.of(new RetrievedChunk(1, 1, "Tài liệu", 0, null, "Nội dung", 0.9)), List.of());

        assertTrue(answer.refused());
        assertEquals(ClaudeAnswerGenerator.REFUSAL_TEXT, answer.text());
        assertTrue(answer.citations().isEmpty());
    }

    /// Haiku 4.5 (và các model cũ hơn) trả 400 nếu request có `output_config.effort` — đổi
    /// `ASSISTANT_CHAT_MODEL` sang model rẻ hơn không được làm hỏng cả luồng hỏi đáp.
    @Test
    void omitsEffortForModelsThatRejectIt() throws IOException {
        responseBody = """
                {"id":"msg_3","type":"message","role":"assistant","model":"claude-haiku-4-5",
                 "content":[{"type":"text","text":"Phí gửi 15.000đ."}],
                 "stop_reason":"end_turn","stop_sequence":null,
                 "usage":{"input_tokens":120,"output_tokens":9}}
                """;
        ClaudeAnswerGenerator haiku = new ClaudeAnswerGenerator(
                new AssistantProperties("test-key", "claude-haiku-4-5", 2048, 30, "", "voyage-4",
                        "http://unused", 1024, 5000),
                AnthropicOkHttpClient.builder()
                        .apiKey("test-key")
                        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .maxRetries(0)
                        .build());

        haiku.generate("Phí gửi bao nhiêu?",
                List.of(new RetrievedChunk(1, 1, "Điều khoản", 0, null, "Phí gửi 15.000đ.", 0.9)), List.of());

        JsonNode request = new ObjectMapper().readTree(requestBody.get());
        assertEquals("claude-haiku-4-5", request.path("model").asText());
        assertTrue(request.path("output_config").isMissingNode(), "Haiku 4.5 không nhận output_config");
        assertTrue(request.path("messages").get(0).path("content").get(0)
                .path("citations").path("enabled").asBoolean(), "vẫn phải bật citations");
    }

    @Test
    void knowsWhichModelsAcceptEffort() {
        assertTrue(ClaudeAnswerGenerator.supportsEffort("claude-opus-5"));
        assertTrue(ClaudeAnswerGenerator.supportsEffort("claude-sonnet-5"));
        assertFalse(ClaudeAnswerGenerator.supportsEffort("claude-haiku-4-5"));
        assertFalse(ClaudeAnswerGenerator.supportsEffort("claude-sonnet-4-5"));
    }

    @Test
    void missingKeyIsReportedAsProviderError() {
        ClaudeAnswerGenerator unconfigured = new ClaudeAnswerGenerator(new AssistantProperties(
                "", "claude-opus-5", 2048, 30, "", "voyage-4", "http://unused", 1024, 5000));

        assertFalse(unconfigured.configured());
        assertThrows(ProviderException.class, () -> unconfigured.generate("?", List.of(), List.of()));
    }
}
