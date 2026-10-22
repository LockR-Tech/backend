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
        assertRoutedTo("/api/admin/feedback", "lb://order-service");
        assertRoutedTo("/api/admin/feedback/7/reply", "lb://order-service");
        assertRoutedTo("/api/admin/analytics/satisfaction", "lb://order-service");
        assertRoutedTo("/api/admin/dashboard/peak-hours", "lb://order-service");
    }

    /// Callback SePay ngoài /api vẫn tới payment-service; đường VNPay cũ không còn route nào.
    @Test
    void paymentCallbackRoutesKeepOnlySepay() {
        assertRoutedTo("/payments/sepay/callback", "lb://payment-service");
        assertRoutedTo("/api/payments/sepay/webhook", "lb://payment-service");
        assertTrue(matchingRouteUris("/payments/vnpay/callback").isEmpty());
    }

    /// /api/drone-technician/** mặc định về locker-service; các đường của order-service phải đứng
    /// trước để gateway (khớp route đầu tiên) đưa đúng chỗ.
    @Test
    void droneTechnicianOrderPathsRouteToOrderServiceFirst() {
        assertFirstRoutedTo("/api/drone-technician/drone-orders/21/advance", "lb://order-service");
        assertFirstRoutedTo("/api/drone-technician/drone-incidents", "lb://order-service");
        assertFirstRoutedTo("/api/drone-technician/drone-incidents/7", "lb://order-service");
        assertFirstRoutedTo("/api/drone-technician/drones", "lb://locker-service");
        assertFirstRoutedTo("/api/orders/incoming", "lb://order-service");
        assertFirstRoutedTo("/api/orders/delegated-to-me", "lb://order-service");
        assertFirstRoutedTo("/api/orders/incoming/42", "lb://order-service");
        assertFirstRoutedTo("/api/orders/delegated-to-me/42", "lb://order-service");
    }

    /// Actuator không đi qua JwtGatewayFilter: endpoint `gateway` cho thêm/xoá route lúc chạy,
    /// metrics/prometheus/sbom lộ thông tin nội bộ — chỉ được mở health/info.
    @Test
    void actuatorExposesOnlyHealthAndInfo() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();

        assertEquals("health,info", props.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("none", props.getProperty("management.endpoint.gateway.access"));
    }

    private void assertFirstRoutedTo(String path, String expectedUri) {
        List<String> uris = matchingRouteUris(path);
        assertTrue(!uris.isEmpty(), path + " must match a route");
        assertEquals(expectedUri, uris.get(0), path);
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
