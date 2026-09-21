package com.huynqb.laundrylocker.assistant.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.huynqb.laundrylocker.assistant.config.AssistantProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
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

    /// voyage-4 nhận tới 1000 input / 320K token mỗi lần; đoạn ~2000 ký tự ⇒ 64 đoạn còn xa giới hạn.
    private static final int BATCH_SIZE = 64;

    private final AssistantProperties properties;
    private final RestClient restClient;

    public VoyageEmbeddingProvider(AssistantProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = builder.baseUrl(properties.embeddingBaseUrl()).requestFactory(factory).build();
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
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            vectors.addAll(embedBatch(texts.subList(from, Math.min(texts.size(), from + BATCH_SIZE)), type));
        }
        return vectors;
    }

    private List<float[]> embedBatch(List<String> batch, InputType type) {
        EmbeddingResponse response;
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
        } catch (RestClientException ex) {
            throw new ProviderException("Embedding request failed: " + ex.getMessage(), ex);
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
