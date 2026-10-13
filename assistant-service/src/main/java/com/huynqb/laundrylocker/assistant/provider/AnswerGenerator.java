package com.huynqb.laundrylocker.assistant.provider;

import com.huynqb.laundrylocker.assistant.knowledge.RetrievedChunk;

import java.util.List;

/// Sinh câu trả lời chỉ từ các đoạn tài liệu truy xuất được (và dữ liệu đơn của người hỏi nếu có),
/// kèm trích dẫn.
public interface AnswerGenerator {

    boolean configured();

    String model();

    /// `history` là các lượt trước, xen kẽ USER/ASSISTANT, bắt đầu bằng USER. `extra` (có thể null) là
    /// tài liệu dựng lúc hỏi — hiện là "Đơn hàng của bạn" — gửi sau các đoạn, nên trích dẫn trỏ vào nó
    /// có `chunkIndex == chunks.size()`.
    GeneratedAnswer generate(String question, List<RetrievedChunk> chunks, List<ChatTurn> history, ContextDocument extra);

    default GeneratedAnswer generate(String question, List<RetrievedChunk> chunks, List<ChatTurn> history) {
        return generate(question, chunks, history, null);
    }

    record ChatTurn(boolean fromUser, String content) {
    }

    /// Tài liệu không nằm trong kho tri thức, dựng riêng cho câu hỏi này. `context` là dòng mô tả
    /// ngắn (ví dụ thời điểm lấy dữ liệu), cũng dùng làm "mục" của nguồn trả về app.
    record ContextDocument(String title, String context, String content) {
    }

    /// `chunkIndex` là vị trí đoạn trong danh sách `chunks` đã gửi (bằng `chunks.size()` ⇒ tài liệu `extra`).
    record Citation(int chunkIndex, String citedText) {
    }

    record GeneratedAnswer(String text, boolean refused, List<Citation> citations, long inputTokens, long outputTokens) {
    }
}
