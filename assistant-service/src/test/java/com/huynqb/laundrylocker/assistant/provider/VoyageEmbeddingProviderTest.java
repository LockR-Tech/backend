package com.huynqb.laundrylocker.assistant.provider;

import com.huynqb.laundrylocker.assistant.config.AssistantProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VoyageEmbeddingProviderTest {

    private static final AssistantProperties PROPERTIES = new AssistantProperties(
            "", "claude-haiku-4-5", 2048, 30, "voyage-key", "voyage-4", "http://voyage.test", 2, 5000);
    private static final String ONE_VECTOR = "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://voyage.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final List<Duration> waits = new ArrayList<>();

    private VoyageEmbeddingProvider provider() {
        VoyageEmbeddingProvider provider = new VoyageEmbeddingProvider(PROPERTIES, builder.build());
        provider.sleeper(waits::add);
        return provider;
    }

    @Test
    void batchesSplitByCharacterBudgetAndCount() {
        String big = "x".repeat(VoyageEmbeddingProvider.MAX_BATCH_CHARS / 2 + 1);
        assertThat(VoyageEmbeddingProvider.batches(List.of(big, big, big))).hasSize(3);
        assertThat(VoyageEmbeddingProvider.batches(Collections.nCopies(130, "a"))).hasSize(3);
        String huge = "y".repeat(VoyageEmbeddingProvider.MAX_BATCH_CHARS * 2);
        assertThat(VoyageEmbeddingProvider.batches(List.of("a", huge, "b")))
                .extracting(List::size).containsExactly(1, 1, 1);
    }

    @Test
    void documentEmbeddingWaitsAndRetriesOnRateLimit() {
        HttpHeaders retryAfter = new HttpHeaders();
        retryAfter.add(HttpHeaders.RETRY_AFTER, "20");
        server.expect(once(), requestTo("http://voyage.test/embeddings"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(retryAfter));
        server.expect(once(), requestTo("http://voyage.test/embeddings"))
                .andRespond(withSuccess(ONE_VECTOR, MediaType.APPLICATION_JSON));

        List<float[]> vectors = provider().embed(List.of("đoạn tài liệu"), EmbeddingProvider.InputType.DOCUMENT);

        assertThat(vectors).hasSize(1);
        assertThat(waits).containsExactly(Duration.ofSeconds(20));
        server.verify();
    }

    @Test
    void queryEmbeddingFailsFastOnRateLimit() {
        server.expect(once(), requestTo("http://voyage.test/embeddings"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider().embed(List.of("câu hỏi"), EmbeddingProvider.InputType.QUERY))
                .isInstanceOf(ProviderException.class);
        assertThat(waits).isEmpty();
        server.verify();
    }

    @Test
    void retryAfterFallsBackToOneMinuteWindow() {
        assertThat(VoyageEmbeddingProvider.retryAfter(null)).isEqualTo(VoyageEmbeddingProvider.DEFAULT_RATE_LIMIT_WAIT);
        HttpHeaders dated = new HttpHeaders();
        dated.add(HttpHeaders.RETRY_AFTER, "Wed, 21 Oct 2026 07:28:00 GMT");
        assertThat(VoyageEmbeddingProvider.retryAfter(dated)).isEqualTo(VoyageEmbeddingProvider.DEFAULT_RATE_LIMIT_WAIT);
    }
}
