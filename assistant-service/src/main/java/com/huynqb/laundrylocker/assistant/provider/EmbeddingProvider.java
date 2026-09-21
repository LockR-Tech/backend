package com.huynqb.laundrylocker.assistant.provider;

import java.util.List;

/// Biến văn bản thành vector để tìm đoạn tài liệu gần câu hỏi. Số chiều phải khớp cột
/// `kb_chunks.embedding`.
public interface EmbeddingProvider {

    enum InputType { DOCUMENT, QUERY }

    boolean configured();

    String model();

    /// Kết quả cùng thứ tự với `texts`.
    List<float[]> embed(List<String> texts, InputType type);
}
