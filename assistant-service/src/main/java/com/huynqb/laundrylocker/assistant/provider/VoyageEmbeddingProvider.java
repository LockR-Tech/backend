package com.huynqb.laundrylocker.assistant.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.huynqb.laundrylocker.assistant.config.AssistantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/// Voyage AI `POST /v1/embeddings` (nhà cung cấp embedding Anthropic khuyến nghị; đa ngôn ngữ, có
/// tiếng Việt). Tài liệu nhúng với `input_type=document`, câu hỏi với `query`.
@Component
public class VoyageEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(VoyageEmbeddingProvider.class);

    /// voyage-4 nhận tới 1000 input / 320K token mỗi lần; đoạn ~2000 ký tự ⇒ 64 đoạn còn xa giới hạn.
    private static final int BATCH_SIZE = 64;

    /// Tài khoản Voyage chưa gắn phương thức thanh toán bị giới hạn 3 request + 10K token mỗi phút: một
    /// tài liệu dài gửi một lần sẽ luôn 429. Chia lô theo số ký tự (tiếng Việt có dấu ~2 ký tự/token ⇒
    /// ~6K token mỗi lô) để mỗi lô lọt hạn mức phút.
    static final int MAX_BATCH_CHARS = 12_000;

    /// Đánh chỉ mục chạy nền nên gặp 429 thì chờ rồi thử lại; câu hỏi của người dùng thì báo lỗi ngay.
    static final int MAX_RATE_LIMIT_RETRIES = 4;
    static final Duration DEFAULT_RATE_LIMIT_WAIT = Duration.ofSeconds(62);

    private final AssistantProperties properties;
    private final RestClient restClient;
    private Sleeper sleeper = duration -> Thread.sleep(duration.toMillis());

    @Autowired
    public VoyageEmbeddingProvider(AssistantProperties properties, RestClient.Builder builder) {
        this(properties, buildClient(properties, builder));
    }

    VoyageEmbeddingProvider(AssistantProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient buildClient(AssistantProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return builder.baseUrl(properties.embeddingBaseUrl()).requestFactory(factory).build();
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    void sleeper(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    @Override
    public boolean configured() {
        return properties.embeddingConfigured();
    }

    @Override
    public String model() {
        return properties.embeddingModel();
    }

    @Override
    public List<float[]> embed(List<String> texts, InputType type) {
        if (!configured()) {
            throw new ProviderException("EMBEDDING_API_KEY is not configured");
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (List<String> batch : batches(texts)) {
            vectors.addAll(embedBatch(batch, type));
        }
        return vectors;
    }

    /// Lô liên tiếp, mỗi lô ≤ [#BATCH_SIZE] đoạn và ≤ [#MAX_BATCH_CHARS] ký tự (một đoạn dài hơn thì đi riêng).
    static List<List<String>> batches(List<String> texts) {
        List<List<String>> batches = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int chars = 0;
        for (String text : texts) {
            int length = text == null ? 0 : text.length();
            if (!current.isEmpty() && (current.size() >= BATCH_SIZE || chars + length > MAX_BATCH_CHARS)) {
                batches.add(current);
                current = new ArrayList<>();
                chars = 0;
            }
            current.add(text);
            chars += length;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    private List<float[]> embedBatch(List<String> batch, InputType type) {
        EmbeddingResponse response;
        int attempt = 0;
        while (true) {
            try {
                response = restClient.post()
                        .uri("/embeddings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.embeddingApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new EmbeddingRequest(
                                batch, properties.embeddingModel(), type.name().toLowerCase(Locale.ROOT),
                                properties.embeddingDimension()))
                        .retrieve()
                        .body(EmbeddingResponse.class);
                break;
            } catch (HttpClientErrorException.TooManyRequests ex) {
                if (type != InputType.DOCUMENT || attempt >= MAX_RATE_LIMIT_RETRIES) {
                    throw new ProviderException("Embedding request failed: " + ex.getMessage(), ex);
                }
                attempt++;
                Duration wait = retryAfter(ex.getResponseHeaders());
                log.info("Voyage rate limit (429), waiting {}s before retry {}/{}",
                        wait.toSeconds(), attempt, MAX_RATE_LIMIT_RETRIES);
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ProviderException("Embedding retry interrupted", interrupted);
                }
            } catch (RestClientException ex) {
                throw new ProviderException("Embedding request failed: " + ex.getMessage(), ex);
            }
        }
        if (response == null || response.data() == null || response.data().size() != batch.size()) {
            throw new ProviderException("Embedding response does not match the request");
        }
        return response.data().stream()
                .sorted(Comparator.comparingInt(EmbeddingItem::index))
                .map(item -> {
                    if (item.embedding() == null || item.embedding().length != properties.embeddingDimension()) {
                        throw new ProviderException("Embedding dimension mismatch: expected "
                                + properties.embeddingDimension());
                    }
                    return item.embedding();
                })
                .toList();
    }

    /// `Retry-After` (giây) nếu Voyage gửi, không thì chờ hết cửa sổ một phút.
    static Duration retryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value != null) {
            try {
                long seconds = Long.parseLong(value.trim());
                if (seconds > 0 && seconds <= 300) {
                    return Duration.ofSeconds(seconds);
                }
            } catch (NumberFormatException ignored) {
                // dạng ngày giờ HTTP — dùng mặc định
            }
        }
        return DEFAULT_RATE_LIMIT_WAIT;
    }

    record EmbeddingRequest(
            List<String> input,
            String model,
            @JsonProperty("input_type") String inputType,
            @JsonProperty("output_dimension") int outputDimension) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(List<EmbeddingItem> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingItem(int index, float[] embedding) {
    }
}
