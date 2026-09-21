package com.huynqb.laundrylocker.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/// Cấu hình hạ tầng (biến môi trường trên VM). Ngưỡng/giới hạn nghiệp vụ nằm ở
/// [com.huynqb.laundrylocker.assistant.settings.AssistantRules] để admin chỉnh trên web.
@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProperties(
        String anthropicApiKey,
        String chatModel,
        long maxOutputTokens,
        int chatTimeoutSeconds,
        String embeddingApiKey,
        String embeddingModel,
        String embeddingBaseUrl,
        int embeddingDimension,
        long indexPollMs) {

    public boolean chatConfigured() {
        return anthropicApiKey != null && !anthropicApiKey.isBlank();
    }

    public boolean embeddingConfigured() {
        return embeddingApiKey != null && !embeddingApiKey.isBlank();
    }
}
