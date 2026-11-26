package com.huynqb.laundrylocker.assistant.provider;

/// Dịch câu hỏi viết bằng ngôn ngữ khác (thường là tiếng Anh) sang tiếng Việt để tìm trong kho tài liệu
/// tiếng Việt (F3-G15). Câu trả lời vẫn theo ngôn ngữ người hỏi — đây chỉ là câu dùng để tìm.
public interface QueryTranslator {

    boolean configured();

    /// Bản tiếng Việt của [text]; lỗi nhà cung cấp hoặc kết quả không dùng được ⇒ [ProviderException].
    String toVietnamese(String text);
}
