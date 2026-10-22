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

    // Header định danh chỉ gateway được gắn (từ JWT đã kiểm); client tự gửi thì bỏ.
    private static final List<String> USER_HEADERS = List.of("X-User-Id", "X-Account-Id", "X-User-Roles");

    private final Environment environment;
    private final AccountStatusChecker accountStatus;
    private SecretKey key;

    public JwtGatewayFilter(Environment environment, AccountStatusChecker accountStatus) {
        this.environment = environment;
        this.accountStatus = accountStatus;
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
                            && !isCustomerLockerAction(path)
                            && !isTechnicianBoxFault(path);
            if (!hasRequiredRole(path, exchange.getRequest().getMethod(), roles)
                    || (mutatingLockerStructure && !hasAny(roles, "ADMIN"))) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
            ServerWebExchange forwarded = withUserHeaders(exchange, claims, roles);
            // Tài khoản bị khoá sau khi cấp token ⇒ 401 để client làm mới token, việc đó cũng bị
            // auth-service từ chối nên người dùng bị đăng xuất.
            return accountStatus.isActive(accountId(claims)).flatMap(active -> {
                if (!active) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
                return chain.filter(forwarded);
            });
        } catch (Exception ex) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private Mono<Void> enrichIfPresent(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(withoutUserHeaders(exchange));
        }
        Claims claims;
        try {
            claims = parse(authHeader.substring(7));
        } catch (Exception ex) {
            return chain.filter(withoutUserHeaders(exchange));
        }
        if (!isAccessToken(claims)) {
            return chain.filter(withoutUserHeaders(exchange));
        }
        // Đường công khai: tài khoản bị khoá vẫn gọi được nhưng như khách vãng lai.
        return accountStatus.isActive(accountId(claims)).flatMap(active -> chain.filter(
                active ? withUserHeaders(exchange, claims, extractRoles(claims)) : withoutUserHeaders(exchange)));
    }

    private ServerWebExchange withoutUserHeaders(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        if (USER_HEADERS.stream().noneMatch(headers::containsKey)) {
            return exchange;
        }
        ServerHttpRequest request =
                exchange.getRequest().mutate().headers(h -> USER_HEADERS.forEach(h::remove)).build();
        return exchange.mutate().request(request).build();
    }

    private static Long accountId(Claims claims) {
        return claims.get("accountId") instanceof Number id ? id.longValue() : null;
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
    // Role model: CUSTOMER, ADMIN (web console), LOCKER_TECHNICIAN (locker upkeep
    // + IoT), DRONE_TECHNICIAN (drone team). MANAGER/STAFF were retired.
    //
    // Route prefixes mirror the roles:
    //   /api/drone-technician/**  drone fleet, dispatch queue, drone orders
    //   /api/locker-technician/** faults, reports, box actions, landing pad, IoT
    //   /api/maintenance/**       lịch bảo trì + inspection log, dùng chung vì cả
    //                             hai KTV đều xem lịch của mảng mình
    private boolean hasRequiredRole(
            String path, org.springframework.http.HttpMethod method, List<String> roles) {
        if (hasAny(roles, "ADMIN")) {
            return true;
        }
        // KTV tủ chỉ được ĐỌC danh sách phiếu bản admin (app dùng làm dự phòng); giao,
        // đóng, gia hạn SLA bản admin là việc của ADMIN — KTV có endpoint riêng của mình.
        if (path.equals("/api/admin/lockers/reports")) {
            return org.springframework.http.HttpMethod.GET.equals(method) && hasAny(roles, "LOCKER_TECHNICIAN");
        }
        if (path.startsWith("/api/admin")) {
            return false;
        }
        if (path.startsWith("/api/drone-technician")) {
            return hasAny(roles, "DRONE_TECHNICIAN");
        }
        if (path.startsWith("/api/locker-technician")) {
            return hasAny(roles, "LOCKER_TECHNICIAN");
        }
        // Báo ô hỏng trực tiếp và đẩy trạng thái thiết bị/ô là việc vận hành. Khách báo hỏng qua
        // đơn của mình (/api/orders/{id}/report-box-fault); Pi gửi trạng thái qua MQTT.
        if (isTechnicianBoxFault(path)
                || path.equals("/api/iot/device-status")
                || path.equals("/api/iot/box-status")) {
            return hasAny(roles, "LOCKER_TECHNICIAN");
        }
        // Sửa lịch và đổi KTV phụ trách lịch là việc của ADMIN; KTV chỉ xem và hoàn tất kiểm tra.
        if (path.startsWith("/api/maintenance/schedules/")
                && org.springframework.http.HttpMethod.PUT.equals(method)) {
            return false;
        }
        if (path.startsWith("/api/maintenance")) {
            return hasAny(roles, "DRONE_TECHNICIAN", "LOCKER_TECHNICIAN");
        }
        return true;
    }

    // Cabinet/box structure changes are operator work; customers only report
    // faults, file reports, rate a resolved report, or trigger an open on
    // their own cell.
    private boolean isCustomerLockerAction(String path) {
        return path.endsWith("/report")
                || path.endsWith("/open")
                || path.endsWith("/rate")
                // Người báo bổ sung ảnh hiện trường; locker-service tự kiểm chủ phiếu.
                || (path.startsWith("/api/lockers/reports/") && path.endsWith("/attachments"));
    }

    private static final java.util.regex.Pattern BOX_FAULT_PATH =
            java.util.regex.Pattern.compile("^/api/boxes/\\d+/fault$");

    private boolean isTechnicianBoxFault(String path) {
        return BOX_FAULT_PATH.matcher(path).matches();
    }

    // Giá/giới hạn hiển thị cho khách (ADR-0005) — chỉ đúng endpoint public, không mở /api/admin/settings.
    private static final java.util.regex.Pattern PUBLIC_SETTINGS_PATH =
            java.util.regex.Pattern.compile("^/api/settings/[a-z]+/public$");

    // Eureka application ids the discovery locator would turn into a path prefix
    // (lower-case-service-id: true). Anything addressed this way is a bypass attempt.
    private static final List<String> SERVICE_ID_PREFIXES =
            List.of(
                    "api-gateway",
                    "assistant-service",
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
                || path.equals("/api/iot/unlock-with-code")
                // Chỉ có hiệu lực khi ô của đơn vừa được mở thành công bằng chính mã đó.
                || path.equals("/api/iot/confirm-drop-with-code")
                || path.equals("/api/iot/end-rental-with-code")) {
            return true;
        }
        if (path.startsWith("/api/auth")
                || path.startsWith("/api/admin/auth")
                || path.equals("/")
                || path.startsWith("/ws")
                || path.equals("/actuator/health")
                || path.startsWith("/actuator/health/")
                || path.equals("/actuator/info")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/webjars")
                // Cổng SePay gọi webhook / trình duyệt quay về sau thanh toán — không có JWT.
                || path.startsWith("/api/payments/sepay")
                || path.startsWith("/payments/sepay/callback")) {
            return true;
        }
        // Catalogue browsing is anonymous; any mutation requires a JWT.
        // Ví voucher (/api/promotions/vouchers/**) là dữ liệu cá nhân — cần JWT
        // để gateway gắn X-User-Id thật, không cho client tự chèn header.
        boolean readOnly = org.springframework.http.HttpMethod.GET.equals(method);
        return readOnly
                && (PUBLIC_SETTINGS_PATH.matcher(path).matches()
                || path.startsWith("/api/stores")
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
