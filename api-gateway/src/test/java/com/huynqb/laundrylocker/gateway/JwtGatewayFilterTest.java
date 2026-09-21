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
    void revenueReportsRequireAdminRole() {
        for (String path : new String[]{"/api/admin/revenue/summary", "/api/admin/payments/stats"}) {
            MockServerWebExchange customer =
                    exchangeWithBearer(MockServerHttpRequest.get(path), token("access", "CUSTOMER"));
            AtomicBoolean customerForwarded = new AtomicBoolean(false);
            filter.filter(customer, chainThatMarks(customerForwarded)).block();
            assertEquals(HttpStatus.FORBIDDEN, customer.getResponse().getStatusCode(), path);
            assertFalse(customerForwarded.get(), path);

            MockServerWebExchange anonymous = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
            AtomicBoolean anonymousForwarded = new AtomicBoolean(false);
            filter.filter(anonymous, chainThatMarks(anonymousForwarded)).block();
            assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getResponse().getStatusCode(), path);

            MockServerWebExchange admin =
                    exchangeWithBearer(MockServerHttpRequest.get(path), token("access", "ADMIN"));
            AtomicBoolean adminForwarded = new AtomicBoolean(false);
            filter.filter(admin, chainThatMarks(adminForwarded)).block();
            assertTrue(adminForwarded.get(), path);
        }
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

    // Trước khi tách prefix, KTV drone vào được cả việc của tủ (lỗ hổng F3.02).
    @Test
    void blocksDroneTechnicianFromLockerTechnicianApi() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/locker-technician/reports"),
                        token("access", "DRONE_TECHNICIAN"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void allowsLockerTechnicianForLockerTechnicianApi() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/locker-technician/reports"),
                        token("access", "LOCKER_TECHNICIAN"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void allowsLockerTechnicianForIotDevices() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/locker-technician/devices"),
                        token("access", "LOCKER_TECHNICIAN"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void blocksLockerTechnicianFromDroneFleetApi() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/drone-technician/drones"),
                        token("access", "LOCKER_TECHNICIAN"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void blocksLockerTechnicianFromDroneDeliveryQueue() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/drone-technician/drone-deliveries"),
                        token("access", "LOCKER_TECHNICIAN"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    @Test
    void allowsDroneTechnicianForDroneDeliveryQueue() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/drone-technician/drone-deliveries"),
                        token("access", "DRONE_TECHNICIAN"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void allowsDroneTechnicianForDroneFleetApi() {
        MockServerWebExchange exchange =
                exchangeWithBearer(
                        MockServerHttpRequest.get("/api/drone-technician/drones"),
                        token("access", "DRONE_TECHNICIAN"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainThatMarks(chainCalled)).block();

        assertTrue(chainCalled.get());
    }

    // Lịch bảo trì dùng chung: cả hai KTV đều phải vào được.
    @Test
    void allowsBothTechniciansForSharedScheduleApi() {
        for (String role : new String[] {"LOCKER_TECHNICIAN", "DRONE_TECHNICIAN"}) {
            MockServerWebExchange exchange =
                    exchangeWithBearer(
                            MockServerHttpRequest.get("/api/maintenance/schedules"),
                            token("access", role));
            AtomicBoolean chainCalled = new AtomicBoolean(false);

            filter.filter(exchange, chainThatMarks(chainCalled)).block();

            assertTrue(chainCalled.get(), role + " phải vào được lịch bảo trì dùng chung");
        }
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
    void allowsCustomerToAddPhotosToOwnReportButNotOtherLockerMutations() {
        MockServerWebExchange photos =
                exchangeWithBearer(
                        MockServerHttpRequest.post("/api/lockers/reports/9/attachments"), token("access", "CUSTOMER"));
        AtomicBoolean photosForwarded = new AtomicBoolean(false);
        filter.filter(photos, chainThatMarks(photosForwarded)).block();
        assertTrue(photosForwarded.get());

        MockServerWebExchange structure =
                exchangeWithBearer(
                        MockServerHttpRequest.post("/api/lockers/9/attachments"), token("access", "CUSTOMER"));
        AtomicBoolean structureForwarded = new AtomicBoolean(false);
        filter.filter(structure, chainThatMarks(structureForwarded)).block();
        assertEquals(HttpStatus.FORBIDDEN, structure.getResponse().getStatusCode());
        assertFalse(structureForwarded.get());
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

    @Test
    void publicSettingsAreAnonymousButAdminSettingsNeedAdmin() {
        assertPublicGet("/api/settings/order/public");

        MockServerWebExchange anonymousAdmin =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/settings/order").build());
        AtomicBoolean anonymousForwarded = new AtomicBoolean(false);
        filter.filter(anonymousAdmin, chainThatMarks(anonymousForwarded)).block();
        assertEquals(HttpStatus.UNAUTHORIZED, anonymousAdmin.getResponse().getStatusCode());
        assertFalse(anonymousForwarded.get());

        MockServerWebExchange customerAdmin = exchangeWithBearer(
                MockServerHttpRequest.put("/api/admin/settings/order"), token("access", "CUSTOMER"));
        AtomicBoolean customerForwarded = new AtomicBoolean(false);
        filter.filter(customerAdmin, chainThatMarks(customerForwarded)).block();
        assertEquals(HttpStatus.FORBIDDEN, customerAdmin.getResponse().getStatusCode());
        assertFalse(customerForwarded.get());

        MockServerWebExchange notPublic =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/settings/order/public/../x").build());
        AtomicBoolean notPublicForwarded = new AtomicBoolean(false);
        filter.filter(notPublic, chainThatMarks(notPublicForwarded)).block();
        assertFalse(notPublicForwarded.get());
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
