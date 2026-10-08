package com.huynqb.laundrylocker.assistant.knowledge;

/// Một đoạn đã đánh chỉ mục — đúng phần trợ lý đọc khi trả lời (không kèm vector).
public record KnowledgeChunk(long id, int ordinal, String heading, String content) {
}
