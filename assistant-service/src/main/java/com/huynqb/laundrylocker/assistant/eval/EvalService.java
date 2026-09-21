package com.huynqb.laundrylocker.assistant.eval;

import com.huynqb.laundrylocker.assistant.knowledge.ChunkRepository;
import com.huynqb.laundrylocker.assistant.knowledge.RetrievedChunk;
import com.huynqb.laundrylocker.assistant.knowledge.RoleSet;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator;
import com.huynqb.laundrylocker.assistant.provider.EmbeddingProvider;
import com.huynqb.laundrylocker.assistant.provider.ProviderException;
import com.huynqb.laundrylocker.assistant.settings.AssistantRules;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/// Bộ câu hỏi đánh giá chất lượng truy xuất: câu phải trúng đúng tài liệu, câu ngoài phạm vi (hoặc
/// tài liệu nội bộ mà vai trò đó không được đọc) phải bị từ chối. Mặc định chỉ đo truy xuất (chỉ tốn
/// phí nhúng); `generate=true` gọi thêm Claude cho từng câu — tốn phí mô hình.
@Service
public class EvalService {

    public record EvalCase(
            long id, String question, List<String> roles, String expectedDocumentTitle, boolean mustRefuse,
            LocalDateTime createdAt) {
    }

    public record EvalCaseRequest(
            @NotBlank @Size(max = 2000) String question,
            List<String> roles,
            @Size(max = 255) String expectedDocumentTitle,
            boolean mustRefuse) {
    }

    public record EvalResult(
            long caseId, String question, List<String> roles, String expectedDocumentTitle, boolean mustRefuse,
            double topScore, List<String> retrievedTitles, boolean passed, String answer) {
    }

    public record EvalReport(
            int total, int passed, int retrievalCases, int retrievalHits, int refusalCases, int refusalCorrect,
            double minScore, List<EvalResult> results) {
    }

    private static final int GENERATION_THREADS = 4;

    private final JdbcTemplate jdbc;
    private final EmbeddingProvider embeddings;
    private final ChunkRepository chunks;
    private final AnswerGenerator generator;
    private final AssistantRules rules;

    public EvalService(JdbcTemplate jdbc, EmbeddingProvider embeddings, ChunkRepository chunks,
                       AnswerGenerator generator, AssistantRules rules) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.generator = generator;
        this.rules = rules;
    }

    public List<EvalCase> list() {
        return jdbc.query(
                "SELECT id, question, roles, expected_document_title, must_refuse, created_at FROM eval_cases ORDER BY id",
                (rs, i) -> {
                    Timestamp created = rs.getTimestamp("created_at");
                    return new EvalCase(
                            rs.getLong("id"),
                            rs.getString("question"),
                            Arrays.stream(rs.getString("roles").split(",")).map(String::trim)
                                    .filter(StringUtils::hasText).toList(),
                            rs.getString("expected_document_title"),
                            rs.getBoolean("must_refuse"),
                            created == null ? null : created.toLocalDateTime());
                });
    }

    public List<EvalCase> add(List<EvalCaseRequest> requests) {
        for (EvalCaseRequest request : requests) {
            if (!request.mustRefuse() && !StringUtils.hasText(request.expectedDocumentTitle())) {
                throw new BusinessException(
                        "EVAL_CASE_INVALID", "Câu không bắt buộc từ chối phải có tài liệu mong đợi");
            }
            List<String> roles = RoleSet.normalizeAllowed(
                    request.roles() == null || request.roles().isEmpty() ? List.of("CUSTOMER") : request.roles());
            jdbc.update(
                    "INSERT INTO eval_cases (question, roles, expected_document_title, must_refuse) VALUES (?, ?, ?, ?)",
                    request.question().trim(), String.join(",", roles),
                    StringUtils.hasText(request.expectedDocumentTitle()) ? request.expectedDocumentTitle().trim() : null,
                    request.mustRefuse());
        }
        return list();
    }

    public void delete(long id) {
        if (jdbc.update("DELETE FROM eval_cases WHERE id = ?", id) == 0) {
            throw new NotFoundException("EvalCase", id);
        }
    }

    public EvalReport run(boolean generate) {
        if (!embeddings.configured() || (generate && !generator.configured())) {
            throw new BusinessException(
                    "ASSISTANT_NOT_CONFIGURED", "Trợ lý hỏi đáp chưa được cấu hình trên máy chủ",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        List<EvalCase> cases = list();
        double minScore = rules.minScore();
        int topK = rules.topK();
        List<EvalResult> results = new ArrayList<>();
        int passed = 0;
        int retrievalCases = 0;
        int retrievalHits = 0;
        int refusalCases = 0;
        int refusalCorrect = 0;
        try {
            List<float[]> vectors = cases.isEmpty()
                    ? List.of()
                    : embeddings.embed(cases.stream().map(EvalCase::question).toList(), EmbeddingProvider.InputType.QUERY);
            List<List<RetrievedChunk>> relevantByCase = new ArrayList<>();
            List<Double> topScores = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                EvalCase evalCase = cases.get(i);
                List<String> readerRoles = evalCase.roles().contains(RoleSet.ADMIN)
                        ? null
                        : RoleSet.readerRoles(evalCase.roles());
                List<RetrievedChunk> found = chunks.search(vectors.get(i), readerRoles, topK);
                // Điểm cao nhất kể cả dưới ngưỡng — để chỉnh ngưỡng.
                topScores.add(found.isEmpty() ? 0 : found.get(0).score());
                relevantByCase.add(found.stream().filter(chunk -> chunk.score() >= minScore).toList());
            }
            List<String> answers = generate ? generateAnswers(cases, relevantByCase) : null;
            for (int i = 0; i < cases.size(); i++) {
                EvalCase evalCase = cases.get(i);
                List<RetrievedChunk> relevant = relevantByCase.get(i);
                double topScore = topScores.get(i);
                List<String> titles = relevant.stream().map(RetrievedChunk::documentTitle).distinct().toList();
                boolean ok;
                if (evalCase.mustRefuse()) {
                    refusalCases++;
                    ok = relevant.isEmpty();
                    if (ok) {
                        refusalCorrect++;
                    }
                } else {
                    retrievalCases++;
                    ok = titles.stream().anyMatch(title -> title.equalsIgnoreCase(evalCase.expectedDocumentTitle()));
                    if (ok) {
                        retrievalHits++;
                    }
                }
                if (ok) {
                    passed++;
                }
                results.add(new EvalResult(
                        evalCase.id(), evalCase.question(), evalCase.roles(), evalCase.expectedDocumentTitle(),
                        evalCase.mustRefuse(), topScore, titles, ok, answers == null ? null : answers.get(i)));
            }
        } catch (ProviderException ex) {
            throw new BusinessException(
                    "ASSISTANT_UNAVAILABLE", "Nhà cung cấp mô hình đang lỗi, thử lại sau", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new EvalReport(
                cases.size(), passed, retrievalCases, retrievalHits, refusalCases, refusalCorrect, minScore, results);
    }

    /// Gọi Claude song song (4 luồng) để cả bộ câu hỏi xong trong giới hạn đọc 300 s của Nginx.
    private List<String> generateAnswers(List<EvalCase> cases, List<List<RetrievedChunk>> relevantByCase) {
        ExecutorService pool = Executors.newFixedThreadPool(GENERATION_THREADS);
        try {
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                String question = cases.get(i).question();
                List<RetrievedChunk> relevant = relevantByCase.get(i);
                futures.add(relevant.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.supplyAsync(
                                () -> generator.generate(question, relevant, List.of()).text(), pool));
            }
            return futures.stream().map(EvalService::join).toList();
        } finally {
            pool.shutdownNow();
        }
    }

    private static String join(CompletableFuture<String> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof ProviderException provider) {
                throw provider;
            }
            throw ex;
        }
    }
}
