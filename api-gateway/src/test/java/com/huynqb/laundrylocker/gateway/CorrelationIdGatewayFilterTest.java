package com.huynqb.laundrylocker.gateway;

import com.huynqb.laundrylocker.common.web.CorrelationIds;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CorrelationIdGatewayFilterTest {

    private final CorrelationIdGatewayFilter filter = new CorrelationIdGatewayFilter();

    @Test
    void forwardsExistingCorrelationId() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/orders")
                                .header(CorrelationIds.HEADER_NAME, "trace-2026-xyz")
                                .build());
        AtomicReference<String> forwarded = new AtomicReference<>();

        filter.filter(exchange, captureForwardedHeader(forwarded)).block();

        assertEquals("trace-2026-xyz", forwarded.get());
        assertEquals("trace-2026-xyz", exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER_NAME));
    }

    @Test
    void generatesMissingCorrelationId() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders").build());
        AtomicReference<String> forwarded = new AtomicReference<>();

        filter.filter(exchange, captureForwardedHeader(forwarded)).block();

        assertNotNull(forwarded.get());
        assertEquals(forwarded.get(), exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER_NAME));
    }

    private GatewayFilterChain captureForwardedHeader(AtomicReference<String> forwarded) {
        return exchange -> {
            forwarded.set(exchange.getRequest().getHeaders().getFirst(CorrelationIds.HEADER_NAME));
            return reactor.core.publisher.Mono.empty();
        };
    }
}
