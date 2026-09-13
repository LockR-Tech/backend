package com.huynqb.laundrylocker.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtGatewayFilterTest {

    private static final String SECRET = "test-jwt-secret-at-least-32-bytes-long";

    private JwtGatewayFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtGatewayFilter(new MockEnvironment());
        ReflectionTestUtils.setField(filter, "secret", SECRET);
        filter.init();
    }

    @Test
    void blocksInternalEndpointsBeforeAuthentication() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/internal/orders/123").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void blocksDiscoveryLocatorBypassToAdminApi() {
        // Regression: with the discovery locator on, /user-service/api/admin/users
        // reached the admin API with a plain CUSTOMER token because the RBAC check
        // only matches a leading /api/admin.
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/user-service/api/admin/users"),
                        token("access", "CUSTOMER"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void blocksDiscoveryLocatorBypassToInternalApi() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/user-service/internal/users/1"),
                        token("access", "CUSTOMER"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void blocksInternalSegmentThatIsNotAtTheStart() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/internal/users/1").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void rejectsRefreshTokenForBusinessApi() {
        MockServerWebExchange exchange =
                exchangeWithBearer(MockServerHttpRequest.get("/api/orders/my-orders"), token("refresh", "CUSTOMER"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void requiresAdminForAdminApi() {
        MockServerWebExchange exchange =
                exchangeWithBearer(MockServerHttpRequest.get("/api/admin/orders"), token("access", "CUSTOMER"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void forwardsIdentityHeadersForAuthenticatedCustomer() {
        MockServerWebExchange exchange =
                exchangeWithBearer(MockServerHttpRequest.get("/api/orders/my-orders"), token("access", "CUSTOMER"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, captureExchange(forwarded)).block();

        assertEquals("42", forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("7", forwarded.get().getRequest().getHeaders().getFirst("X-Account-Id"));
        assertEquals("CUSTOMER", forwarded.get().getRequest().getHeaders().getFirst("X-User-Roles"));
    }

    @Test
    void allowsMaintenanceForMaintenanceApi() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/maintenance/reports"), token("access", "MAINTENANCE"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void allowsTechnicianForLockerMaintenanceApi() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/maintenance/reports"), token("access", "TECHNICIAN"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void blocksTechnicianFromDroneFleetApi() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/maintenance/drones"), token("access", "TECHNICIAN"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void blocksTechnicianFromDroneDeliveryQueue() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/maintenance/drone-deliveries"),
                        token("access", "TECHNICIAN"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void allowsMaintenanceForDroneDeliveryQueue() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/maintenance/drone-deliveries"),
                        token("access", "MAINTENANCE"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void allowsMaintenanceForDroneFleetApi() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/maintenance/drones"), token("access", "MAINTENANCE"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void allowsCustomerToRateResolvedReport() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.post("/api/lockers/reports/9/rate"), token("access", "CUSTOMER"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void keepsOpenApiAndSwaggerUiPublic() {
        assertPublicGet("/v3/api-docs");
        assertPublicGet("/v3/api-docs/order-service");
        assertPublicGet("/swagger-ui/index.html");
    }

    @Test
    void keepsPromotionCatalogPublicButVoucherWalletProtected() {
        assertPublicGet("/api/promotions/active");
        assertPublicGet("/api/promotions/validate/SUMMER20");

        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/promotions/vouchers/my").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.filter(exchange, chainThatMarks(chainCalled)).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void allowsKioskUnlockEndpointsWithoutJwtButKeepsOtherIotProtected() {
        for (String path :
                new String[]{
                        "/api/iot/verify-pin", "/api/iot/verify-access", "/api/iot/unlock", "/api/iot/unlock-with-code"
                }) {
            MockServerWebExchange exchange =
                    MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
            AtomicBoolean chainCalled = new AtomicBoolean(false);
            filter.filter(exchange, chainThatMarks(chainCalled)).block();
            assertTrue(chainCalled.get(), path + " should be public for the kiosk");
        }

        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.post("/api/iot/pickup").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.filter(exchange, chainThatMarks(chainCalled)).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void keepsCatalogueGetPublicButRequiresJwtForCatalogueMutation() {
        assertPublicGet("/api/lockers/2/layout");

        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.post("/api/lockers").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    private void assertPublicGet(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertTrue(chainCalled.get());
    }

    private MockServerWebExchange exchangeWithBearer(MockServerHttpRequest.BaseBuilder<?> request, String token) {
        return MockServerWebExchange.from(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());
    }

    private GatewayFilterChain chainThatMarks(AtomicBoolean called) {
        return exchange -> {
            called.set(true);
            return Mono.empty();
        };
    }

    private GatewayFilterChain captureExchange(AtomicReference<ServerWebExchange> forwarded) {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    private String token(String tokenUse, String... roles) {
        Instant now = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("42")
                .claim("accountId", 7L)
                .claim("roles", List.of(roles))
                .claim("tokenUse", tokenUse)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }
}
