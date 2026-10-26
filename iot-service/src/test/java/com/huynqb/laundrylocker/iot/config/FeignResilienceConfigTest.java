package com.huynqb.laundrylocker.iot.config;

import feign.FeignException;
import feign.Request;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Feign của iot-service chạy qua circuit breaker Resilience4j (mặc định TimeLimiter 1s, mọi exception
/// đều tính là lỗi). Kiosk tra mã sai ⇒ order-service trả 400; nếu 400 bị đếm thì vài lần gõ sai đã mở
/// circuit và cả mã đúng cũng bị báo "Hệ thống đang bận".
class FeignResilienceConfigTest {

    private static final String CB = "resilience4j.circuitbreaker.configs.default.";

    @Test
    void clientErrorsDoNotOpenTheCircuit() throws Exception {
        CircuitBreaker breaker = CircuitBreaker.of("orderLookup", configFromYaml());

        for (int i = 0; i < 30; i++) {
            call(breaker, () -> { throw clientError(); });
        }

        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }

    @Test
    void serverErrorsStillOpenTheCircuit() throws Exception {
        CircuitBreaker breaker = CircuitBreaker.of("orderLookup", configFromYaml());

        for (int i = 0; i < 30; i++) {
            call(breaker, () -> { throw serverError(); });
        }

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
    }

    @Test
    void timeLimiterOutlastsFeignTimeouts() {
        Properties props = load();
        String limiter = props.getProperty("resilience4j.timelimiter.configs.default.timeout-duration");
        assertNotNull(limiter, "iot-service must override the 1s Resilience4j TimeLimiter default");
        Duration timeLimit = Duration.parse("PT" + defaultOf(limiter).toUpperCase());
        long connectMs = Long.parseLong(defaultOf(props.getProperty("spring.cloud.openfeign.client.config.default.connectTimeout")));
        long readMs = Long.parseLong(defaultOf(props.getProperty("spring.cloud.openfeign.client.config.default.readTimeout")));

        assertTrue(timeLimit.toMillis() > connectMs + readMs,
                "TimeLimiter " + timeLimit + " must exceed connect + read timeout (" + (connectMs + readMs) + "ms)");
    }

    private static CircuitBreakerConfig configFromYaml() throws ClassNotFoundException {
        Properties props = load();
        List<Class<? extends Throwable>> ignored = new ArrayList<>();
        for (int i = 0; props.getProperty(CB + "ignore-exceptions[" + i + "]") != null; i++) {
            ignored.add(Class.forName(props.getProperty(CB + "ignore-exceptions[" + i + "]")).asSubclass(Throwable.class));
        }
        assertFalse(ignored.isEmpty(), "4xx Feign errors must be ignored by the circuit breaker");
        @SuppressWarnings("unchecked")
        Class<? extends Throwable>[] ignoredArray = ignored.toArray(new Class[0]);
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(intOf(props, "sliding-window-size"))
                .minimumNumberOfCalls(intOf(props, "minimum-number-of-calls"))
                .failureRateThreshold(intOf(props, "failure-rate-threshold"))
                .ignoreExceptions(ignoredArray)
                .build();
    }

    private static void call(CircuitBreaker breaker, Supplier<Object> body) {
        try {
            breaker.executeSupplier(body);
        } catch (RuntimeException expected) {
            // Lỗi 4xx/5xx hoặc CallNotPermittedException khi circuit đã mở.
        }
    }

    private static FeignException clientError() {
        return new FeignException.BadRequest("ORDER_NOT_FOUND", request(), null, Map.of());
    }

    private static FeignException serverError() {
        return new FeignException.InternalServerError("boom", request(), null, Map.of());
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "http://order-service/internal/orders/by-access",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private static int intOf(Properties props, String key) {
        return Integer.parseInt(defaultOf(props.getProperty(CB + key)));
    }

    private static Properties load() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject();
    }

    /// `${ENV:default}` ⇒ default; giá trị thường giữ nguyên.
    private static String defaultOf(String value) {
        String v = value.trim();
        if (v.startsWith("${") && v.endsWith("}") && v.contains(":")) {
            return v.substring(v.indexOf(':') + 1, v.length() - 1);
        }
        return v;
    }
}
