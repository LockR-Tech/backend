package com.huynqb.laundrylocker.assistant.knowledge;

import com.huynqb.laundrylocker.assistant.provider.EmbeddingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/// Đánh chỉ mục tài liệu PENDING: đọc → chia đoạn → nhúng → ghi đoạn. Trạng thái nằm trong DB nên
/// khởi động lại không mất việc; gọi API nhúng nằm ngoài transaction.
@Slf4j
@Component
public class KnowledgeIndexer {

    /// ~3 triệu ký tự; chặn tài liệu khổng lồ làm tốn chi phí nhúng.
    static final int MAX_CHUNKS = 1500;

    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final DocumentParser parser;
    private final TextChunker chunker;
    private final EmbeddingProvider embeddings;
    private final TransactionTemplate transaction;

    public KnowledgeIndexer(DocumentRepository documents, ChunkRepository chunks, DocumentParser parser,
                            TextChunker chunker, EmbeddingProvider embeddings, TransactionTemplate transaction) {
        this.documents = documents;
        this.chunks = chunks;
        this.parser = parser;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.transaction = transaction;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void requeueInterrupted() {
        int requeued = documents.requeueInterrupted();
        if (requeued > 0) {
            log.info("Requeued {} knowledge document(s) interrupted mid-indexing", requeued);
        }
    }

    /// Chưa có EMBEDDING_API_KEY ⇒ tài liệu nằm PENDING, có khoá là tự chạy tiếp.
    @Scheduled(fixedDelayString = "${app.assistant.index-poll-ms:5000}", initialDelay = 10_000)
    public void indexPending() {
        if (!embeddings.configured()) {
            return;
        }
        Optional<Long> next;
        while ((next = documents.claimNextPending()).isPresent()) {
            index(next.get());
        }
    }

    void index(long documentId) {
        try {
            KnowledgeDocument.Content document = documents.findContent(documentId).orElse(null);
            if (document == null) {
                return;
            }
            List<TextChunker.Chunk> pieces =
                    chunker.chunk(parser.parse(document.fileName(), document.mimeType(), document.bytes()));
            if (pieces.isEmpty()) {
                throw new IllegalArgumentException("Tài liệu không có nội dung chữ để đánh chỉ mục");
            }
            if (pieces.size() > MAX_CHUNKS) {
                throw new IllegalArgumentException(
                        "Tài liệu quá dài (" + pieces.size() + " đoạn, tối đa " + MAX_CHUNKS + ") — hãy tách nhỏ");
            }
            List<float[]> vectors = embeddings.embed(
                    pieces.stream().map(piece -> embeddingInput(document.title(), piece)).toList(),
                    EmbeddingProvider.InputType.DOCUMENT);
            transaction.executeWithoutResult(status -> {
                chunks.replace(documentId, pieces, vectors);
                documents.markReady(documentId, pieces.size());
            });
            log.info("Indexed knowledge document {} into {} chunk(s)", documentId, pieces.size());
        } catch (RuntimeException ex) {
            log.warn("Indexing knowledge document {} failed: {}", documentId, ex.getMessage());
            documents.markFailed(documentId, ex.getMessage());
        }
    }

    /// Nhúng kèm tên tài liệu và mục để đoạn ngắn vẫn mang đủ ngữ cảnh khi tìm.
    static String embeddingInput(String title, TextChunker.Chunk chunk) {
        StringBuilder input = new StringBuilder(title);
        if (StringUtils.hasText(chunk.heading())) {
            input.append(" — ").append(chunk.heading());
        }
        return input.append("\n\n").append(chunk.content()).toString();
    }
}
