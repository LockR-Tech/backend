package com.huynqb.laundrylocker.gateway;

import com.huynqb.laundrylocker.common.security.SecuritySecrets;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    @Value("${app.security.jwt.secret:laundry-locker-microservices-secret-key-change-me-please-32chars}")
    private String secret;

    private final Environment environment;
    private SecretKey key;

    public JwtGatewayFilter(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void init() {
        key =
                SecuritySecrets.hmacShaKeyFor(
                        secret, "app.security.jwt.secret", environment.getActiveProfiles());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        // Spring Cloud Gateway's discovery locator exposes every registered app as
        // /{service-id}/** — a path shape that slips past both the /internal block
        // and the path-prefix RBAC below (/user-service/api/admin/users would reach
        // the admin API with any valid token). The locator is disabled in
        // application.yml; this guard keeps the bypass closed even if it is turned
        // back on. 404 rather than 403: the route legitimately does not exist.
        if (isServiceIdPrefixed(path)) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        // Service-to-service endpoints are reachable only inside the cluster
        // (Feign via Eureka); never through the public gateway. Matched per path
        // segment so no prefix trick can hide the "internal" hop.
        if (hasSegment(path, "internal")) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        if (isPublic(path, exchange.getRequest().getMethod())) {
            return enrichIfPresent(exchange, chain);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            Claims claims = parse(authHeader.substring(7));
            if (!isAccessToken(claims)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            List<String> roles = extractRoles(claims);
            boolean mutatingLockerStructure =
                    !org.springframework.http.HttpMethod.GET.equals(exchange.getRequest().getMethod())
                            && (path.startsWith("/api/lockers") || path.startsWith("/api/boxes"))
                            && !isCustomerLockerAction(path);
            if (!hasRequiredRole(path, roles)
                    || (mutatingLockerStructure && !hasAny(roles, "ADMIN"))) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(withUserHeaders(exchange, claims, roles));
        } catch (Exception ex) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private Mono<Void> enrichIfPresent(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        try {
            Claims claims = parse(authHeader.substring(7));
            if (!isAccessToken(claims)) {
                return chain.filter(exchange);
            }
            return chain.filter(withUserHeaders(exchange, claims, extractRoles(claims)));
        } catch (Exception ex) {
            return chain.filter(exchange);
        }
    }

    private ServerWebExchange withUserHeaders(
            ServerWebExchange exchange, Claims claims, List<String> roles) {
        ServerHttpRequest request =
                exchange
                        .getRequest()
                        .mutate()
                        .header("X-User-Id", claims.getSubject())
                        .header("X-Account-Id", String.valueOf(claims.get("accountId")))
                        .header("X-User-Roles", roles == null ? "" : String.join(",", roles))
                        .build();
        return exchange.mutate().request(request).build();
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get("tokenUse", String.class));
    }

    private List<String> extractRoles(Claims claims) {
        Object value = claims.get("roles");
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        if (value instanceof String roles && !roles.isBlank()) {
            return Arrays.stream(roles.split(",")).map(String::trim).filter(role -> !role.isBlank()).toList();
        }
        return Collections.emptyList();
    }

    // Path-prefix RBAC. ADMIN is a superset of every operational role.
    // Role model: CUSTOMER, ADMIN (web console), TECHNICIAN (locker upkeep + IoT),
    // MAINTENANCE (drone team). MANAGER/STAFF were retired.
    private boolean hasRequiredRole(String path, List<String> roles) {
        if (path.startsWith("/api/admin")) {
            return hasAny(roles, "ADMIN");
        }
        // Drone fleet + drone-delivery dispatch queue are the MAINTENANCE (drone
        // team) surface; the rest of the maintenance API (faults, reports, box
        // actions, schedules, landing pad) belongs to TECHNICIAN.
        if (path.startsWith("/api/maintenance/drone")) {
            return hasAny(roles, "MAINTENANCE", "ADMIN");
        }
        if (path.startsWith("/api/maintenance")) {
            return hasAny(roles, "MAINTENANCE", "TECHNICIAN", "ADMIN");
        }
        if (path.startsWith("/api/technician")) {
            return hasAny(roles, "TECHNICIAN", "ADMIN");
        }
        return true;
    }

    // Cabinet/box structure changes are operator work; customers only report
    // faults, file reports, rate a resolved report, or trigger an open on
    // their own cell.
    private boolean isCustomerLockerAction(String path) {
        return path.endsWith("/fault")
                || path.endsWith("/report")
                || path.endsWith("/open")
                || path.endsWith("/rate");
    }

    // Eureka application ids the discovery locator would turn into a path prefix
    // (lower-case-service-id: true). Anything addressed this way is a bypass attempt.
    private static final List<String> SERVICE_ID_PREFIXES =
            List.of(
                    "api-gateway",
                    "auth-service",
                    "discovery-server",
                    "iot-service",
                    "locker-service",
                    "loyalty-service",
                    "notification-service",
                    "order-service",
                    "payment-service",
                    "store-service",
                    "user-service");

    private boolean isServiceIdPrefixed(String path) {
        return SERVICE_ID_PREFIXES.contains(firstSegment(path));
    }

    private String firstSegment(String path) {
        int start = path.startsWith("/") ? 1 : 0;
        int end = path.indexOf('/', start);
        String segment = end < 0 ? path.substring(start) : path.substring(start, end);
        return segment.toLowerCase();
    }

    private boolean hasSegment(String path, String segment) {
        for (String part : path.split("/")) {
            if (segment.equalsIgnoreCase(part)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAny(List<String> roles, String... required) {
        if (roles == null) {
            return false;
        }
        for (String role : required) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPublic(String path, org.springframework.http.HttpMethod method) {
        // Kiosk màn hình tủ (mô phỏng khi chưa có phần cứng): người nhận đứng tại
        // tủ nhập PIN/QR/mã ủy quyền — bản thân mã là credential, không có JWT.
        // Chỉ mở đúng các endpoint verify/unlock; phần còn lại của /api/iot vẫn
        // yêu cầu JWT. Sai mã bị khóa tạm theo box (access-attempt lockout).
        if (path.equals("/api/iot/verify-pin")
                || path.equals("/api/iot/verify-access")
                || path.equals("/api/iot/unlock")
                || path.equals("/api/iot/unlock-with-code")) {
            return true;
        }
        if (path.startsWith("/api/auth")
                || path.startsWith("/api/admin/auth")
                || path.equals("/")
                || path.startsWith("/ws")
                || path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/webjars")
                || path.startsWith("/api/payments/vnpay")
                || path.startsWith("/api/payments/momo")
                || path.startsWith("/payments/vnpay/callback")) {
            return true;
        }
        // Catalogue browsing is anonymous; any mutation requires a JWT.
        // Ví voucher (/api/promotions/vouchers/**) là dữ liệu cá nhân — cần JWT
        // để gateway gắn X-User-Id thật, không cho client tự chèn header.
        boolean readOnly = org.springframework.http.HttpMethod.GET.equals(method);
        return readOnly
                && (path.startsWith("/api/stores")
                || path.startsWith("/api/lockers")
                || path.startsWith("/api/services")
                || path.startsWith("/api/laundry-services")
                || (path.startsWith("/api/promotions")
                && !path.startsWith("/api/promotions/vouchers")));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
