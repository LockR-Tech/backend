package com.huynqb.laundrylocker.gateway;

import com.huynqb.laundrylocker.common.web.CorrelationIds;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class CorrelationIdGatewayFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId =
                CorrelationIds.resolve(exchange.getRequest().getHeaders().getFirst(CorrelationIds.HEADER_NAME));
        ServerHttpRequest request =
                exchange.getRequest().mutate().header(CorrelationIds.HEADER_NAME, correlationId).build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();
        mutatedExchange.getResponse().getHeaders().set(CorrelationIds.HEADER_NAME, correlationId);
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
