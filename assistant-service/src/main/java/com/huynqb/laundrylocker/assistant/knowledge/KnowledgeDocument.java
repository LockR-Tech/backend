package com.huynqb.laundrylocker.assistant.knowledge;

import java.time.LocalDateTime;
import java.util.List;

/// Tài liệu trong kho tri thức (không kèm nội dung file).
public record KnowledgeDocument(
        long id,
        String title,
        String fileName,
        String mimeType,
        String status,
        List<String> allowedRoles,
        long sizeBytes,
        int chunkCount,
        String error,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime indexedAt) {

    public static final String PENDING = "PENDING";
    public static final String INDEXING = "INDEXING";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";

    /// Nội dung file để đánh chỉ mục.
    public record Content(long id, String title, String fileName, String mimeType, byte[] bytes) {
    }
}
