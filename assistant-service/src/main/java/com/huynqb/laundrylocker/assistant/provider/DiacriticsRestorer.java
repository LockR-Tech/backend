package com.huynqb.laundrylocker.assistant.provider;

/// Thêm dấu cho câu tiếng Việt gõ không dấu ("lam sao de gui hang" → "làm sao để gửi hàng").
public interface DiacriticsRestorer {

    boolean configured();

    /// Câu đã thêm dấu; lỗi nhà cung cấp hoặc kết quả không dùng được ⇒ [ProviderException].
    String restore(String text);
}
