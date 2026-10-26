package com.huynqb.laundrylocker.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Feign chạy qua circuit breaker nên còn bị TimeLimiter cắt (mặc định Resilience4j là 1s).
/// TimeLimiter phải dài hơn mọi readTimeout của Feign, nếu không báo cáo doanh thu trả 503 lúc được lúc không.
class FeignTimeoutConfigTest {

    @Test
    void timeLimiterOutlastsEveryFeignReadTimeout() {
        Properties props = load();
        String limiter = props.getProperty("resilience4j.timelimiter.configs.default.timeout-duration");
        assertNotNull(limiter, "order-service must override the 1s Resilience4j TimeLimiter default");
        Duration timeLimit = Duration.parse("PT" + defaultOf(limiter).toUpperCase());

        props.stringPropertyNames().stream()
                .filter(k -> k.startsWith("spring.cloud.openfeign.client.config.") && k.endsWith(".readTimeout"))
                .forEach(k -> {
                    long readMs = Long.parseLong(defaultOf(props.getProperty(k)));
                    assertTrue(timeLimit.toMillis() > readMs, k + " (" + readMs + "ms) must be below the TimeLimiter " + timeLimit);
                });
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
