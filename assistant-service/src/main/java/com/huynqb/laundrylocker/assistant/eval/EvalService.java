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
            for (int i = 0; i < cases.size(); i++) {
                EvalCase evalCase = cases.get(i);
                List<String> readerRoles = evalCase.roles().contains(RoleSet.ADMIN)
                        ? null
                        : RoleSet.readerRoles(evalCase.roles());
                List<RetrievedChunk> relevant = chunks.search(vectors.get(i), readerRoles, topK).stream()
                        .filter(chunk -> chunk.score() >= minScore)
                        .toList();
                double topScore = relevant.isEmpty() ? 0 : relevant.get(0).score();
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
                String answer = null;
                if (generate && !relevant.isEmpty()) {
                    answer = generator.generate(evalCase.question(), relevant, List.of()).text();
                }
                if (ok) {
                    passed++;
                }
                results.add(new EvalResult(
                        evalCase.id(), evalCase.question(), evalCase.roles(), evalCase.expectedDocumentTitle(),
                        evalCase.mustRefuse(), topScore, titles, ok, answer));
            }
        } catch (ProviderException ex) {
            throw new BusinessException(
                    "ASSISTANT_UNAVAILABLE", "Nhà cung cấp mô hình đang lỗi, thử lại sau", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new EvalReport(
                cases.size(), passed, retrievalCases, retrievalHits, refusalCases, refusalCorrect, minScore, results);
    }
}
