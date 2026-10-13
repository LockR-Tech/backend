package com.huynqb.laundrylocker.assistant.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.huynqb.laundrylocker.assistant.config.AssistantProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

/// Một Anthropic client dùng chung cho câu trả lời và bước thêm dấu (chung connection pool), tạo khi
/// cần lần đầu để service vẫn khởi động được khi chưa đặt `ANTHROPIC_API_KEY`.
@Component
public class ClaudeClientProvider {

    private final AssistantProperties properties;
    private volatile AnthropicClient client;

    @Autowired
    public ClaudeClientProvider(AssistantProperties properties) {
        this.properties = properties;
    }

    /// Cho test trỏ vào máy chủ giả.
    public ClaudeClientProvider(AssistantProperties properties, AnthropicClient client) {
        this.properties = properties;
        this.client = client;
    }

    public boolean configured() {
        return properties.chatConfigured();
    }

    public AnthropicClient client() {
        if (!configured()) {
            throw new ProviderException("ANTHROPIC_API_KEY is not configured");
        }
        AnthropicClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = AnthropicOkHttpClient.builder()
                            .apiKey(properties.anthropicApiKey())
                            .timeout(Duration.ofSeconds(properties.chatTimeoutSeconds()))
                            .maxRetries(1)
                            .build();
                    client = current;
                }
            }
        }
        return current;
    }
}
