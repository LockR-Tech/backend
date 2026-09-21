package com.huynqb.laundrylocker.assistant.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/// DTO của API hỏi đáp.
public final class ChatDtos {

    private ChatDtos() {
    }

    /// `conversationId` null ⇒ mở hội thoại mới.
    public record AskRequest(Long conversationId, @NotBlank @Size(max = 2000) String question) {
    }

    /// Đoạn tài liệu làm căn cứ cho câu trả lời; `citedText` là câu được trích nguyên văn.
    public record SourceView(long documentId, String documentTitle, long chunkId, String heading, String citedText) {
    }

    public record AskResponse(
            long conversationId, long messageId, String answer, boolean refused, List<SourceView> sources,
            LocalDateTime createdAt) {
    }

    public record ConversationView(
            long id, long userId, String title, int messageCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record MessageView(
            long id, String role, String content, boolean refused, List<SourceView> sources, Double topScore,
            LocalDateTime createdAt) {
    }

    public record ConversationDetail(ConversationView conversation, List<MessageView> messages) {
    }

    /// App dùng để ẩn/hiện lối vào trợ lý. `configured` = cả hai khoá; hai cờ sau để admin biết thiếu
    /// khoá nào (thiếu khoá nhúng thì tài liệu nằm PENDING).
    public record AssistantStatus(
            boolean enabled, boolean configured, boolean embeddingConfigured, boolean chatConfigured) {
    }
}
