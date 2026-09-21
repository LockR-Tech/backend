package com.huynqb.laundrylocker.assistant.provider;

import com.huynqb.laundrylocker.assistant.knowledge.RetrievedChunk;

import java.util.List;

/// Sinh câu trả lời chỉ từ các đoạn tài liệu truy xuất được, kèm trích dẫn.
public interface AnswerGenerator {

    boolean configured();

    String model();

    /// `history` là các lượt trước, xen kẽ USER/ASSISTANT, bắt đầu bằng USER.
    GeneratedAnswer generate(String question, List<RetrievedChunk> chunks, List<ChatTurn> history);

    record ChatTurn(boolean fromUser, String content) {
    }

    /// `chunkIndex` là vị trí đoạn trong danh sách `chunks` đã gửi.
    record Citation(int chunkIndex, String citedText) {
    }

    record GeneratedAnswer(String text, boolean refused, List<Citation> citations, long inputTokens, long outputTokens) {
    }
}
