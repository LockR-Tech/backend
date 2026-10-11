package com.huynqb.laundrylocker.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/// Tài khoản bị admin khoá phải mất quyền ngay, không đợi access token hết hạn (mặc định 24 h).
/// Hỏi auth-service theo `accountId` trong token và nhớ kết quả trong `ttl` để không gọi mỗi request.
/// auth-service lỗi hoặc chậm ⇒ cho qua (chữ ký token vẫn hợp lệ), tránh một service hỏng kéo sập
/// toàn bộ API.
@Component
public class AccountStatusChecker {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusChecker.class);
    private static final int MAX_ENTRIES = 10_000;

    private final Function<Long, Mono<Boolean>> lookup;
    private final boolean enabled;
    private final Duration ttl;
    private final Duration timeout;
    private final Clock clock;
    private final Map<Long, CachedStatus> cache = new ConcurrentHashMap<>();

    @Autowired
    public AccountStatusChecker(
            ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> loadBalancer,
            @Value("${app.security.account-status-check.enabled:true}") boolean enabled,
            @Value("${app.security.account-status-check.ttl:30s}") Duration ttl,
            @Value("${app.security.account-status-check.timeout:1s}") Duration timeout) {
        this(authServiceLookup(loadBalancer.getIfAvailable()), enabled, ttl, timeout, Clock.systemUTC());
    }

    AccountStatusChecker(
            Function<Long, Mono<Boolean>> lookup,
            boolean enabled,
            Duration ttl,
            Duration timeout,
            Clock clock) {
        this.lookup = lookup;
        this.enabled = enabled;
        this.ttl = ttl;
        this.timeout = timeout;
        this.clock = clock;
    }

    /// Luôn cho qua (dùng cho test và khi tắt kiểm tra).
    static AccountStatusChecker allowAll() {
        return new AccountStatusChecker(id -> Mono.just(true), false, Duration.ZERO, Duration.ZERO, Clock.systemUTC());
    }

    public Mono<Boolean> isActive(Long accountId) {
        if (!enabled || accountId == null) {
            return Mono.just(true);
        }
        long now = clock.millis();
        CachedStatus cached = cache.get(accountId);
        if (cached != null && cached.expiresAtMillis() > now) {
            return Mono.just(cached.active());
        }
        return lookup.apply(accountId)
                .timeout(timeout)
                .doOnNext(active -> remember(accountId, active, now))
                .onErrorResume(ex -> {
                    log.warn("Không kiểm tra được trạng thái tài khoản {}: {}", accountId, ex.toString());
                    return Mono.just(true);
                })
                .defaultIfEmpty(true);
    }

    private void remember(Long accountId, boolean active, long now) {
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(accountId, new CachedStatus(active, now + ttl.toMillis()));
    }

    private static Function<Long, Mono<Boolean>> authServiceLookup(
            ReactorLoadBalancerExchangeFilterFunction loadBalancer) {
        WebClient.Builder builder = WebClient.builder().baseUrl("http://auth-service");
        if (loadBalancer != null) {
            builder.filter(loadBalancer);
        }
        WebClient client = builder.build();
        return accountId -> client.get()
                .uri("/internal/auth/accounts/{id}/active", accountId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(AccountStatusChecker::readActive);
    }

    // ApiResponse của auth-service: {"code": …, "data": {"accountId": 7, "active": true}}
    static boolean readActive(Map<?, ?> body) {
        Object data = body == null ? null : body.get("data");
        if (data instanceof Map<?, ?> map && map.get("active") instanceof Boolean active) {
            return active;
        }
        return true;
    }

    private record CachedStatus(boolean active, long expiresAtMillis) {
    }
}
