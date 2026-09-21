package com.huynqb.laundrylocker.assistant;

import com.huynqb.laundrylocker.assistant.chat.AssistantService;
import com.huynqb.laundrylocker.assistant.chat.ChatDtos.AskRequest;
import com.huynqb.laundrylocker.assistant.chat.ChatDtos.AskResponse;
import com.huynqb.laundrylocker.assistant.eval.EvalService;
import com.huynqb.laundrylocker.assistant.knowledge.KnowledgeDocument;
import com.huynqb.laundrylocker.assistant.knowledge.KnowledgeIndexer;
import com.huynqb.laundrylocker.assistant.knowledge.KnowledgeService;
import com.huynqb.laundrylocker.assistant.knowledge.RetrievedChunk;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator;
import com.huynqb.laundrylocker.assistant.provider.EmbeddingProvider;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Chạy trên pgvector thật: migration, đánh chỉ mục, tìm vector có lọc vai trò, trả lời/từ chối,
/// giới hạn tần suất. Nhúng và Claude là bản giả (không gọi API ngoài).
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "app.assistant.index-poll-ms=3600000"
})
@Testcontainers(disabledWithoutDocker = true)
class AssistantPostgresContainerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("assistant_db")
            .withUsername("assistant_user")
            .withPassword("assistant_pass");

    static final AtomicInteger generatorCalls = new AtomicInteger();

    @TestConfiguration
    static class FakeProviders {

        /// Túi từ băm vào 1024 chiều: câu chung nhiều từ với đoạn ⇒ cosine cao.
        @Bean
        @Primary
        EmbeddingProvider fakeEmbeddings() {
            return new EmbeddingProvider() {
                @Override
                public boolean configured() {
                    return true;
                }

                @Override
                public String model() {
                    return "fake";
                }

                @Override
                public List<float[]> embed(List<String> texts, InputType type) {
                    List<float[]> vectors = new ArrayList<>();
                    for (String text : texts) {
                        float[] vector = new float[1024];
                        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                            if (!token.isEmpty()) {
                                vector[Math.floorMod(token.hashCode(), 1024)] += 1;
                            }
                        }
                        vectors.add(vector);
                    }
                    return vectors;
                }
            };
        }

        /// Trả lời bằng câu đầu của đoạn tốt nhất, trích dẫn đoạn đó.
        @Bean
        @Primary
        AnswerGenerator fakeGenerator() {
            return new AnswerGenerator() {
                @Override
                public boolean configured() {
                    return true;
                }

                @Override
                public String model() {
                    return "fake-claude";
                }

                @Override
                public GeneratedAnswer generate(String question, List<RetrievedChunk> chunks, List<ChatTurn> history) {
                    generatorCalls.incrementAndGet();
                    String cited = chunks.get(0).content();
                    return new GeneratedAnswer("Theo tài liệu: " + cited, false,
                            List.of(new Citation(0, cited)), 100, 10);
                }
            };
        }
    }

    @Autowired KnowledgeService knowledge;
    @Autowired KnowledgeIndexer indexer;
    @Autowired AssistantService assistant;
    @Autowired EvalService eval;
    @Autowired BusinessSettings settings;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assistant_conversations");
        jdbc.update("DELETE FROM kb_documents");
        jdbc.update("DELETE FROM eval_cases");
        settings.update(Map.of("app.assistant.questions-per-hour", 30), null);
        generatorCalls.set(0);
    }

    @Test
    void indexesAndAnswersOnlyFromDocumentsTheReaderMaySee() {
        KnowledgeDocument refund = upload("refund.md", "Chính sách hoàn tiền", List.of("ALL"),
                "# Hoàn tiền\nHoàn tiền trong 7 ngày làm việc.");
        KnowledgeDocument sop = upload("sop.md", "Quy trình kiểm tra tủ", List.of("LOCKER_TECHNICIAN"),
                "# Kiểm tra tủ\nKiểm tra khoá điện và cảm biến cửa mỗi tuần.");

        indexer.indexPending();

        assertEquals(KnowledgeDocument.READY, knowledge.get(refund.id()).status());
        assertEquals(1, knowledge.get(sop.id()).chunkCount());

        AskResponse answer = assistant.ask(7L, List.of("CUSTOMER"), new AskRequest(null, "Hoàn tiền trong bao lâu?"));
        assertFalse(answer.refused());
        assertEquals("Chính sách hoàn tiền", answer.sources().get(0).documentTitle());

        // Khách không truy xuất được tài liệu nội bộ của KTV ⇒ từ chối, không gọi mô hình.
        int callsBefore = generatorCalls.get();
        AskResponse internal = assistant.ask(
                7L, List.of("CUSTOMER"), new AskRequest(null, "Kiểm tra khoá điện và cảm biến cửa thế nào?"));
        assertTrue(internal.refused());
        assertTrue(internal.sources().isEmpty());
        assertEquals(callsBefore, generatorCalls.get());

        AskResponse technician = assistant.ask(
                42L, List.of("LOCKER_TECHNICIAN"), new AskRequest(null, "Kiểm tra khoá điện và cảm biến cửa thế nào?"));
        assertFalse(technician.refused());
        assertEquals("Quy trình kiểm tra tủ", technician.sources().get(0).documentTitle());

        // Hội thoại được lưu và chỉ chủ hội thoại xem được.
        assertEquals(4, assistant.myConversation(answer.conversationId(), 7L).messages().size()
                + assistant.myConversation(internal.conversationId(), 7L).messages().size());
        assertThrows(RuntimeException.class, () -> assistant.myConversation(answer.conversationId(), 42L));
    }

    @Test
    void followUpQuestionContinuesTheConversation() {
        upload("refund.md", "Chính sách hoàn tiền", List.of("ALL"), "Hoàn tiền trong 7 ngày làm việc.");
        indexer.indexPending();

        AskResponse first = assistant.ask(7L, List.of("CUSTOMER"), new AskRequest(null, "Hoàn tiền trong bao lâu?"));
        AskResponse second = assistant.ask(7L, List.of("CUSTOMER"), new AskRequest(first.conversationId(), "Tính cả cuối tuần không?"));

        assertEquals(first.conversationId(), second.conversationId());
        assertEquals(4, assistant.myConversation(first.conversationId(), 7L).messages().size());
    }

    @Test
    void rateLimitCountsQuestionsPerUser() {
        upload("refund.md", "Chính sách hoàn tiền", List.of("ALL"), "Hoàn tiền trong 7 ngày làm việc.");
        indexer.indexPending();
        settings.update(Map.of("app.assistant.questions-per-hour", 1), null);

        assistant.ask(7L, List.of("CUSTOMER"), new AskRequest(null, "Hoàn tiền trong bao lâu?"));
        BusinessException limited = assertThrows(BusinessException.class,
                () -> assistant.ask(7L, List.of("CUSTOMER"), new AskRequest(null, "Hoàn tiền trong bao lâu?")));

        assertEquals("ASSISTANT_RATE_LIMITED", limited.getCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.getStatus());
        assistant.ask(8L, List.of("CUSTOMER"), new AskRequest(null, "Hoàn tiền trong bao lâu?"));
    }

    @Test
    void duplicateUploadsAndBadRolesAreRejectedAndUnreadableFilesFail() {
        upload("refund.md", "Chính sách hoàn tiền", List.of("ALL"), "Hoàn tiền trong 7 ngày làm việc.");
        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> upload("copy.md", "Bản sao", List.of("ALL"), "Hoàn tiền trong 7 ngày làm việc."));
        assertEquals("DOCUMENT_DUPLICATE", duplicate.getCode());
        assertEquals("ROLE_INVALID", assertThrows(BusinessException.class,
                () -> upload("x.md", "X", List.of("PARTNER"), "abc")).getCode());

        KnowledgeDocument broken = knowledge.upload(
                "broken.pdf", "application/pdf", "không phải pdf".getBytes(StandardCharsets.UTF_8),
                "Hỏng", List.of("ALL"), 1L);
        indexer.indexPending();
        KnowledgeDocument failed = knowledge.get(broken.id());
        assertEquals(KnowledgeDocument.FAILED, failed.status());
        assertNotNull(failed.error());

        assertEquals(KnowledgeDocument.PENDING, knowledge.reindex(broken.id()).status());
    }

    // Đánh chỉ mục lại không rút tài liệu khỏi câu trả lời trong lúc chờ.
    @Test
    void reindexingKeepsServingTheOldChunks() {
        KnowledgeDocument refund = upload("refund.md", "Chính sách hoàn tiền", List.of("ALL"),
                "Hoàn tiền trong 7 ngày làm việc.");
        indexer.indexPending();

        assertEquals(KnowledgeDocument.PENDING, knowledge.reindex(refund.id()).status());
        AskResponse answer = assistant.ask(7L, List.of("CUSTOMER"), new AskRequest(null, "Hoàn tiền trong bao lâu?"));

        assertFalse(answer.refused());
        assertEquals("Chính sách hoàn tiền", answer.sources().get(0).documentTitle());
    }

    @Test
    void evalWithGenerationAnswersOnlyCasesThatRetrievedSomething() {
        upload("refund.md", "Chính sách hoàn tiền", List.of("ALL"), "Hoàn tiền trong 7 ngày làm việc.");
        indexer.indexPending();
        eval.add(List.of(
                new EvalService.EvalCaseRequest("Hoàn tiền trong bao lâu?", List.of("CUSTOMER"), "Chính sách hoàn tiền", false),
                new EvalService.EvalCaseRequest("Giá vàng hôm nay?", List.of("CUSTOMER"), null, true)));

        EvalService.EvalReport report = eval.run(true);

        assertEquals(1, generatorCalls.get());
        assertNotNull(report.results().get(0).answer());
        assertNull(report.results().get(1).answer());
        assertTrue(report.results().get(0).topScore() > 0);
    }

    @Test
    void evalMeasuresRetrievalHitsAndCorrectRefusals() {
        upload("refund.md", "Chính sách hoàn tiền", List.of("ALL"), "Hoàn tiền trong 7 ngày làm việc.");
        upload("sop.md", "Quy trình kiểm tra tủ", List.of("LOCKER_TECHNICIAN"),
                "Kiểm tra khoá điện và cảm biến cửa mỗi tuần.");
        indexer.indexPending();
        eval.add(List.of(
                new EvalService.EvalCaseRequest("Hoàn tiền trong bao lâu?", List.of("CUSTOMER"), "Chính sách hoàn tiền", false),
                new EvalService.EvalCaseRequest("Kiểm tra khoá điện thế nào?", List.of("CUSTOMER"), null, true),
                new EvalService.EvalCaseRequest("Kiểm tra khoá điện thế nào?", List.of("LOCKER_TECHNICIAN"),
                        "Quy trình kiểm tra tủ", false)));

        EvalService.EvalReport report = eval.run(false);

        assertEquals(3, report.total());
        assertEquals(3, report.passed());
        assertEquals(2, report.retrievalHits());
        assertEquals(1, report.refusalCorrect());
        assertEquals(0, generatorCalls.get());
    }

    private KnowledgeDocument upload(String fileName, String title, List<String> roles, String content) {
        return knowledge.upload(fileName, "text/markdown", content.getBytes(StandardCharsets.UTF_8), title, roles, 1L);
    }
}
