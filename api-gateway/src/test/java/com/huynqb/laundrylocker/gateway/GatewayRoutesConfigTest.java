package com.huynqb.laundrylocker.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Kiểm tra bảng route khai báo trong application.yml: API admin báo cáo phải tới đúng service.
class GatewayRoutesConfigTest {

    private static final String ROUTES = "spring.cloud.gateway.server.webflux.routes";
    private final AntPathMatcher matcher = new AntPathMatcher();

    @Test
    void adminReportingPathsRouteToOwningService() {
        assertRoutedTo("/api/admin/revenue/summary", "lb://order-service");
        assertRoutedTo("/api/admin/revenue/customers/44", "lb://order-service");
        assertRoutedTo("/api/admin/orders/search", "lb://order-service");
        assertRoutedTo("/api/admin/orders/12/detail", "lb://order-service");
        assertRoutedTo("/api/admin/payments/search", "lb://payment-service");
        assertRoutedTo("/api/admin/payments/stats", "lb://payment-service");
        assertRoutedTo("/api/admin/payments/refunds", "lb://payment-service");
        assertRoutedTo("/api/admin/payments/wallet-transactions", "lb://payment-service");
    }

    private void assertRoutedTo(String path, String expectedUri) {
        List<String> uris = matchingRouteUris(path);
        assertEquals(1, uris.size(), path + " must match exactly one route but matched " + uris);
        assertEquals(expectedUri, uris.get(0), path);
    }

    private List<String> matchingRouteUris(String path) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();
        assertTrue(props != null && props.containsKey(ROUTES + "[0].id"), "routes not found in application.yml");

        List<String> uris = new ArrayList<>();
        for (int i = 0; props.containsKey(ROUTES + "[" + i + "].id"); i++) {
            for (int j = 0; props.containsKey(ROUTES + "[" + i + "].predicates[" + j + "]"); j++) {
                String predicate = props.getProperty(ROUTES + "[" + i + "].predicates[" + j + "]");
                if (!predicate.startsWith("Path=")) {
                    continue;
                }
                boolean matches = Arrays.stream(predicate.substring("Path=".length()).split(","))
                        .map(String::trim)
                        .anyMatch(pattern -> matcher.match(pattern, path));
                if (matches) {
                    uris.add(props.getProperty(ROUTES + "[" + i + "].uri"));
                }
            }
        }
        return uris;
    }
}
