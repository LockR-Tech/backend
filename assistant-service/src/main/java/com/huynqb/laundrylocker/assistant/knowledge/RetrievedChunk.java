package com.huynqb.laundrylocker.assistant.knowledge;

/// Một đoạn tài liệu tìm được cho câu hỏi; `score` là độ giống cosine (0..1, lớn = gần).
public record RetrievedChunk(
        long chunkId,
        long documentId,
        String documentTitle,
        int ordinal,
        String heading,
        String content,
        double score) {
}
