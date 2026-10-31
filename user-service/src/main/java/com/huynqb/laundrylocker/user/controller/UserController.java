package com.huynqb.laundrylocker.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.media.MediaUpload;
import com.huynqb.laundrylocker.common.security.UserRoles;
import com.huynqb.laundrylocker.user.client.AuthClient;
import com.huynqb.laundrylocker.user.client.NotificationClient;
import com.huynqb.laundrylocker.user.dto.AdminCreateUserRequest;
import com.huynqb.laundrylocker.user.dto.AdminUserView;
import com.huynqb.laundrylocker.user.dto.UserGrowthPoint;
import com.huynqb.laundrylocker.user.dto.UserProfileRequest;
import com.huynqb.laundrylocker.user.dto.UserProfileView;
import com.huynqb.laundrylocker.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final AuthClient authClient;
    private final NotificationClient notificationClient;
    private final ObjectMapper objectMapper;

    // /api/users/** đi qua gateway với mọi JWT hợp lệ, nên quyền kiểm ở đây: tạo/liệt kê chỉ ADMIN
    // (web dùng /api/admin/users), đọc/sửa theo id chỉ chính chủ hoặc ADMIN.
    @PostMapping("/api/users")
    public ApiResponse<UserSummary> create(
            @RequestBody UserProfileRequest request,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        requireAdmin(roles);
        return ApiResponse.ok("USER_CREATED", "User created", userProfileService.create(request));
    }

    @PostMapping("/api/user")
    public ApiResponse<UserSummary> createLegacy(
            @RequestBody UserProfileRequest request,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return create(request, roles);
    }

    @PutMapping("/api/users/{id}")
    public ApiResponse<UserSummary> update(
            @PathVariable Long id,
            @RequestBody UserProfileRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long callerId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        requireSelfOrAdmin(id, callerId, roles);
        return ApiResponse.ok("USER_UPDATED", "User updated", updateSyncingLogin(id, withoutAdminFields(request)));
    }

    private static void requireAdmin(String roles) {
        if (!UserRoles.isAdmin(roles)) {
            throw new BusinessException("USER_FORBIDDEN", "Không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN);
        }
    }

    private static void requireSelfOrAdmin(Long userId, Long callerId, String roles) {
        if (!Objects.equals(userId, callerId)) {
            requireAdmin(roles);
        }
    }

    /// Đường sửa hồ sơ không phải admin: bỏ trạng thái/vai trò khỏi request (chỉ admin đổi được,
    /// qua /api/admin/users/**) — field null nghĩa là giữ nguyên giá trị hiện tại.
    private static UserProfileRequest withoutAdminFields(UserProfileRequest request) {
        return new UserProfileRequest(
                request.email(),
                request.phoneNumber(),
                request.firstName(),
                request.lastName(),
                request.birthday(),
                request.imageUrl(),
                null,
                null,
                request.fullName());
    }

    @GetMapping("/api/users/{id}")
    public ApiResponse<UserSummary> get(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long callerId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        requireSelfOrAdmin(id, callerId, roles);
        return ApiResponse.ok(userProfileService.get(id));
    }

    @GetMapping("/api/user/{id}")
    public ApiResponse<UserSummary> getLegacy(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long callerId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return get(id, callerId, roles);
    }

    @GetMapping("/api/users")
    public ApiResponse<List<UserSummary>> list(
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        requireAdmin(roles);
        return ApiResponse.ok(userProfileService.list());
    }

    @GetMapping("/api/user/read")
    public ApiResponse<List<UserSummary>> listLegacy(
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return list(roles);
    }

    @GetMapping("/api/user/profile")
    public ApiResponse<UserProfileView> profile(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("USER_PROFILE_OK", "User profile", userProfileService.getProfileView(userId, hasPassword(userId)));
    }

    /// Bấm "Đồng ý" Điều khoản dịch vụ trên app (lần đăng nhập đầu, hoặc khi điều khoản đổi phiên bản).
    /// Body `{version?}`; trả lại hồ sơ đã có `termsAccepted = true`.
    @PostMapping("/api/user/terms")
    public ApiResponse<UserProfileView> acceptTerms(
            @RequestHeader("X-User-Id") Long userId, @RequestBody(required = false) Map<String, Object> request) {
        Object version = request == null ? null : request.get("version");
        return ApiResponse.ok("TERMS_ACCEPTED", "Đã đồng ý điều khoản",
                userProfileService.acceptTerms(userId, version == null ? null : version.toString(), hasPassword(userId)));
    }

    /// Body `{firstName?, lastName?, fullName? (app cũ), email?, phoneNumber?, birthday?}` — field thiếu/rỗng = giữ nguyên.
    /// Đổi email/số điện thoại: kiểm trùng rồi đồng bộ sang tài khoản đăng nhập như đường admin, để đăng nhập
    /// bằng định danh mới vẫn được và không trùng tài khoản khác (409 USER_*_TAKEN / AUTH_*_TAKEN).
    @PutMapping("/api/user/profile")
    public ApiResponse<UserSummary> updateProfile(
            @RequestHeader("X-User-Id") Long userId, @RequestBody UserProfileRequest request) {
        return ApiResponse.ok(
                "PROFILE_UPDATED", "Profile updated", updateSyncingLogin(userId, withoutAdminFields(request)));
    }

    /// `hasPassword` lấy từ tài khoản đăng nhập. auth-service lỗi ⇒ true (app hỏi mật khẩu hiện tại như cũ;
    /// auth-service vẫn là nơi quyết định khi đổi mật khẩu).
    private boolean hasPassword(Long userId) {
        try {
            return authClient.accountsByUsers(List.of(userId)).data().stream()
                    .filter(account -> account.get("userId") != null
                            && userId.toString().equals(account.get("userId").toString()))
                    .findFirst()
                    .map(account -> !Boolean.FALSE.equals(account.get("hasPassword")))
                    .orElse(true);
        } catch (Exception ex) {
            log.warn("Could not read password state for user {}: {}", userId, ex.getMessage());
            return true;
        }
    }

    /// Body mới: MediaUpload (ảnh đã upload lên Cloudinary, ADR-0004). Body cũ `{imageUrl}`/`{avatar}` vẫn nhận.
    @PutMapping("/api/user/avatar")
    public ApiResponse<UserSummary> updateAvatar(
            @RequestHeader("X-User-Id") Long userId, @RequestBody Map<String, Object> request) {
        return ApiResponse.ok("AVATAR_UPDATED", "Avatar updated", applyAvatar(userId, userId, request));
    }

    @DeleteMapping("/api/user/avatar")
    public ApiResponse<UserSummary> removeAvatar(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("AVATAR_REMOVED", "Avatar removed", userProfileService.removeAvatar(userId));
    }

    private UserSummary applyAvatar(Long targetUserId, Long actorUserId, Map<String, Object> request) {
        if (request.get("publicId") != null) {
            return userProfileService.updateAvatar(
                    targetUserId, objectMapper.convertValue(request, MediaUpload.class), actorUserId);
        }
        Object imageUrl = request.get("imageUrl") != null ? request.get("imageUrl") : request.get("avatar");
        return userProfileService.updateAvatar(targetUserId, imageUrl == null ? null : String.valueOf(imageUrl));
    }

    /// Body `{currentPassword?, newPassword}`: bắt buộc `currentPassword` khi tài khoản đã có mật khẩu
    /// (`hasPassword`). Refresh token của mọi máy bị thu hồi; `data` là cặp token mới cho máy đang đổi
    /// (`accessToken`, `refreshToken`, `tokenType`, `expiresAt`, ...) để app không bị đăng xuất.
    @PutMapping("/api/user/password")
    public ApiResponse<Map<String, Object>> changePassword(
            @RequestHeader("X-User-Id") Long userId, @RequestBody Map<String, Object> request) {
        Map<String, Object> tokens = fetchFromAuth(() -> authClient.changePassword(userId, request)).data();
        return ApiResponse.ok("PASSWORD_CHANGED", "Password changed successfully", tokens);
    }

    @GetMapping("/api/user/me/statistics")
    public ApiResponse<Map<String, Object>> statistics(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("USER_STATISTICS_RETRIEVED", "User statistics", userProfileService.statistics(userId));
    }

    @GetMapping("/api/user/dashboard")
    public ApiResponse<String> dashboard() {
        return ApiResponse.ok("Admin Dashboard - Access Granted");
    }

    @PostMapping("/api/user/fcm-token")
    public ApiResponse<Void> registerFcmToken(
            @RequestHeader("X-User-Id") Long userId, @RequestBody Map<String, Object> request) {
        Map<String, Object> payload = new HashMap<>(request);
        payload.put("userId", userId);
        notificationClient.saveFcmToken(payload);
        return ApiResponse.ok("FCM_TOKEN_REGISTERED", "FCM token registered");
    }

    @DeleteMapping("/api/user/fcm-token")
    public ApiResponse<Void> removeFcmToken(
            @RequestHeader("X-User-Id") Long userId, @RequestParam String fcmToken) {
        notificationClient.deleteFcmToken(Map.of("userId", userId, "token", fcmToken));
        return ApiResponse.ok("FCM_TOKEN_REMOVED", "FCM token removed");
    }

    @GetMapping("/api/admin/users")
    public ApiResponse<List<AdminUserView>> adminUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role) {
        return ApiResponse.ok(withAuthInfo(userProfileService.listAdminViews(search, status, role)));
    }

    /// Người dùng mới theo tháng cho biểu đồ tăng trưởng (mặc định 12 tháng, tối đa 36, tính cả tháng này).
    @GetMapping("/api/admin/users/growth")
    public ApiResponse<List<UserGrowthPoint>> adminUserGrowth(
            @RequestParam(required = false, defaultValue = "12") Integer months) {
        return ApiResponse.ok(userProfileService.growth(months));
    }

    // Best-effort enrich with auth provider/email-verified (never break the list on lookup failure).
    private List<AdminUserView> withAuthInfo(List<AdminUserView> views) {
        try {
            List<Long> ids = views.stream().map(AdminUserView::id).toList();
            if (ids.isEmpty()) {
                return views;
            }
            Map<Long, Map<String, Object>> byUser =
                    authClient.accountsByUsers(ids).data().stream()
                            .filter(m -> m.get("userId") != null)
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            m -> Long.valueOf(m.get("userId").toString()), m -> m, (a, b) -> a));
            return views.stream()
                    .map(
                            v -> {
                                Map<String, Object> a = byUser.get(v.id());
                                if (a == null) {
                                    return v;
                                }
                                String provider = a.get("provider") == null ? null : a.get("provider").toString();
                                Boolean verified = a.get("emailVerified") instanceof Boolean b ? b : null;
                                return v.withAuth(provider, verified);
                            })
                    .toList();
        } catch (Exception ignored) {
            // auth-service unavailable -> return profile-only views
            return views;
        }
    }

    @PostMapping("/api/admin/users")
    public ApiResponse<UserSummary> adminCreate(@RequestBody AdminCreateUserRequest request) {
        String email = trimmed(request.email());
        String phoneNumber = trimmed(request.phoneNumber());
        String firstName = trimmed(request.firstName());

        // Trước đây request rỗng vẫn được ghi thẳng vào DB rồi mới nổ ở ràng buộc
        // UNIQUE (chuỗi rỗng đụng nhau), khiến admin nhận thông báo "đã tồn tại"
        // cho một email chưa từng dùng.
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (email.isEmpty()) missing.add("email");
        if (firstName.isEmpty()) missing.add("họ tên");
        if (phoneNumber.isEmpty()) missing.add("số điện thoại");
        if (!missing.isEmpty()) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "VALIDATION_ERROR", "Thiếu trường bắt buộc: " + String.join(", ", missing));
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "VALIDATION_ERROR", "Email không hợp lệ: " + email);
        }
        String status = StringUtils.hasText(request.status())
                ? UserProfileService.requireValidStatus(request.status())
                : "ACTIVE";

        userProfileService.assertUnique(email, phoneNumber);

        UserProfileRequest profileRequest = new UserProfileRequest(
                email,
                phoneNumber,
                firstName,
                request.lastName(),
                request.birthday(),
                request.imageUrl(),
                status,
                request.roles()
        );
        UserSummary user = userProfileService.create(profileRequest);
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", user.id());
            payload.put("email", user.email());
            payload.put("phoneNumber", user.phoneNumber());
            payload.put("password", request.password());
            payload.put("roles", request.roles());
            // Tạo sẵn người dùng bị khoá thì tài khoản đăng nhập cũng khoá theo.
            payload.put("status", status);
            authClient.createAccount(payload);
        } catch (Exception ex) {
            // Gỡ profile vừa tạo để không để lại bản ghi nửa vời; auth-service đã
            // tự kiểm tra trùng nên thông báo của nó nói rõ trùng email hay sđt.
            userProfileService.delete(user.id());
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "ACCOUNT_CREATION_FAILED",
                    "Không tạo được tài khoản đăng nhập: " + ex.getMessage());
        }
        return ApiResponse.ok("USER_CREATED", "User created", user);
    }

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    /// Chi tiết cho web admin: các field của UserSummary + createdAt/updatedAt/provider/emailVerified.
    @GetMapping("/api/admin/users/{id}")
    public ApiResponse<AdminUserView> adminGet(@PathVariable Long id) {
        return ApiResponse.ok(withAuthInfo(List.of(userProfileService.getAdminView(id))).get(0));
    }

    /// Admin sửa hồ sơ. Email/số điện thoại/trạng thái đổi thì đồng bộ sang tài khoản đăng nhập
    /// trước khi lưu hồ sơ — auth-service từ chối (trùng, lỗi kết nối) thì hồ sơ giữ nguyên.
    @PutMapping("/api/admin/users/{id}")
    public ApiResponse<UserSummary> adminUpdate(@PathVariable Long id, @RequestBody UserProfileRequest request) {
        return ApiResponse.ok("USER_UPDATED", "User updated", updateSyncingLogin(id, request));
    }

    /// Dùng chung cho admin và chính chủ (request chính chủ đã bỏ trạng thái/vai trò). Email/số điện thoại
    /// đổi ⇒ kiểm trùng bên hồ sơ, rồi đồng bộ sang tài khoản đăng nhập TRƯỚC khi lưu hồ sơ; trạng thái đổi
    /// ⇒ đồng bộ khoá/mở. Email/số điện thoại rỗng = giữ nguyên.
    private UserSummary updateSyncingLogin(Long id, UserProfileRequest request) {
        UserSummary current = userProfileService.get(id);
        String status = StringUtils.hasText(request.status())
                ? UserProfileService.requireValidStatus(request.status())
                : null;
        String email = changedValue(request.email(), current.email(), true);
        String phoneNumber = changedValue(request.phoneNumber(), current.phoneNumber(), false);
        if (email != null || phoneNumber != null) {
            userProfileService.assertUniqueForUpdate(id, email, phoneNumber);
            Map<String, Object> identifiers = new HashMap<>();
            if (email != null) identifiers.put("email", email);
            if (phoneNumber != null) identifiers.put("phoneNumber", phoneNumber);
            callAuth(() -> authClient.updateIdentifiers(id, identifiers));
        }
        if (status != null && !status.equalsIgnoreCase(current.status())) {
            syncAuthStatus(id, status);
        }
        String[] names = resolveNames(request);
        UserProfileRequest normalized = new UserProfileRequest(
                email != null ? email : blankToNull(request.email()),
                phoneNumber != null ? phoneNumber : blankToNull(request.phoneNumber()),
                names[0],
                names[1],
                request.birthday(),
                request.imageUrl(),
                status,
                request.roles());
        return userProfileService.update(id, normalized);
    }

    /// firstName/lastName gửi lên thì dùng nguyên; chỉ có `fullName` (app cũ) thì tách ở khoảng trắng cuối:
    /// "Nguyễn Văn An" ⇒ ("Nguyễn Văn", "An"), một từ ⇒ (từ đó, ""). Không gửi gì ⇒ giữ nguyên (null, null).
    static String[] resolveNames(UserProfileRequest request) {
        if (request.firstName() != null || request.lastName() != null || !StringUtils.hasText(request.fullName())) {
            return new String[] {request.firstName(), request.lastName()};
        }
        String full = request.fullName().trim().replaceAll("\\s+", " ");
        int lastSpace = full.lastIndexOf(' ');
        return lastSpace < 0
                ? new String[] {full, ""}
                : new String[] {full.substring(0, lastSpace), full.substring(lastSpace + 1)};
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /// Giá trị mới (đã trim) nếu khác giá trị hiện tại; null khi không gửi hoặc không đổi.
    private static String changedValue(String requested, String current, boolean ignoreCase) {
        if (!StringUtils.hasText(requested)) {
            return null;
        }
        String value = requested.trim();
        String existing = current == null ? "" : current.trim();
        boolean same = ignoreCase ? value.equalsIgnoreCase(existing) : value.equals(existing);
        return same ? null : value;
    }

    @PutMapping("/api/admin/users/{id}/avatar")
    public ApiResponse<UserSummary> adminUpdateAvatar(
            @PathVariable Long id,
            @RequestBody MediaUpload request,
            @RequestHeader("X-User-Id") Long adminUserId) {
        return ApiResponse.ok(
                "AVATAR_UPDATED", "Avatar updated", userProfileService.updateAvatar(id, request, adminUserId));
    }

    @DeleteMapping("/api/admin/users/{id}/avatar")
    public ApiResponse<UserSummary> adminRemoveAvatar(@PathVariable Long id) {
        return ApiResponse.ok("AVATAR_REMOVED", "Avatar removed", userProfileService.removeAvatar(id));
    }

    /// Khoá/mở người dùng: `?status=` hoặc body `{status: "ACTIVE"|"INACTIVE"}` (thiếu/khác ⇒ 400).
    /// Trạng thái được đẩy sang tài khoản đăng nhập trước (INACTIVE ⇒ chặn đăng nhập + thu hồi
    /// refresh token); auth-service lỗi thì không đổi hồ sơ, tránh báo "đã khoá" mà vẫn đăng nhập được.
    @PutMapping("/api/admin/users/{id}/status")
    public ApiResponse<UserSummary> adminStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, Object> request) {
        Object raw = status != null ? status : request == null ? null : request.get("status");
        String resolved = UserProfileService.requireValidStatus(raw == null ? null : raw.toString());
        userProfileService.get(id);
        syncAuthStatus(id, resolved);
        return ApiResponse.ok("USER_STATUS_UPDATED", "User status updated", userProfileService.updateStatus(id, resolved));
    }

    private void syncAuthStatus(Long userId, String status) {
        callAuth(() -> authClient.updateStatus(userId, Map.of("status", status)));
    }

    private void callAuth(Runnable call) {
        fetchFromAuth(() -> {
            call.run();
            return null;
        });
    }

    private <T> T fetchFromAuth(Supplier<T> call) {
        try {
            return call.get();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw authFailure(ex);
        }
    }

    /// Lỗi nghiệp vụ của auth-service (VD 409 AUTH_EMAIL_TAKEN) trả nguyên mã + câu; còn lại ⇒ 502.
    private BusinessException authFailure(Exception ex) {
        feign.FeignException feignError = null;
        for (Throwable cause = ex; cause != null && feignError == null; cause = cause.getCause()) {
            if (cause instanceof feign.FeignException fe) {
                feignError = fe;
            }
        }
        if (feignError != null && feignError.status() >= 400 && feignError.status() < 500) {
            try {
                com.fasterxml.jackson.databind.JsonNode body = objectMapper.readTree(feignError.contentUTF8());
                String code = body.path("code").asText(null);
                String message = body.path("message").asText(null);
                HttpStatus httpStatus = HttpStatus.resolve(feignError.status());
                if (code != null && message != null && httpStatus != null) {
                    return new BusinessException(code, message, httpStatus);
                }
            } catch (Exception ignored) {
                // Body không phải JSON — dùng lỗi chung bên dưới.
            }
        }
        log.warn("auth-service sync failed: {}", ex.getMessage());
        return new BusinessException(
                "AUTH_SYNC_FAILED", "Không cập nhật được tài khoản đăng nhập, vui lòng thử lại", HttpStatus.BAD_GATEWAY);
    }

    @PutMapping("/api/admin/users/{id}/roles")
    public ApiResponse<UserSummary> adminRoles(@PathVariable Long id, @RequestBody Map<String, Set<String>> request) {
        return ApiResponse.ok("USER_ROLES_UPDATED", "User roles updated", userProfileService.updateRoles(id, request.get("roles")));
    }

    /// Xoá hồ sơ mà để nguyên tài khoản đăng nhập thì người dùng vẫn đăng nhập được, và tài khoản đó vẫn giữ
    /// email/số điện thoại nên không tạo lại người dùng mới với chúng được ⇒ auth-service khoá hẳn tài khoản
    /// (thu hồi refresh token) và nhả định danh trước; auth-service lỗi thì không xoá. Gọi lại an toàn nên
    /// xoá hồ sơ lỗi sau bước này thì admin chỉ cần xoá lại. Đơn hàng/thanh toán vẫn giữ id người dùng cũ.
    @DeleteMapping("/api/admin/users/{id}")
    public ApiResponse<Void> adminDelete(@PathVariable Long id) {
        userProfileService.get(id);
        callAuth(() -> authClient.deleteAccount(id));
        userProfileService.delete(id);
        return ApiResponse.ok("USER_DELETED", "User deleted");
    }

    @GetMapping("/api/admin/hello")
    public ApiResponse<String> helloAdmin() {
        return ApiResponse.ok("Hello Admin");
    }

    @GetMapping("/")
    public ApiResponse<String> home() {
        return ApiResponse.ok("Welcome to Laundry Locker");
    }

    @GetMapping("/secured")
    public ApiResponse<String> secured() {
        return ApiResponse.ok("Secured endpoint");
    }

    @PostMapping("/internal/users")
    public ApiResponse<UserSummary> provision(@RequestBody UserProfileRequest request) {
        return ApiResponse.ok("USER_PROVISIONED", "User provisioned", userProfileService.create(request));
    }

    @GetMapping("/internal/users/{id}")
    public ApiResponse<UserSummary> getInternal(@PathVariable Long id) {
        return ApiResponse.ok(userProfileService.get(id));
    }

    @GetMapping("/internal/users/by-phone")
    public ApiResponse<UserSummary> getByPhoneInternal(@org.springframework.web.bind.annotation.RequestParam String phone) {
        return ApiResponse.ok(userProfileService.getByPhone(phone));
    }

    @GetMapping("/internal/users")
    public ApiResponse<List<UserSummary>> listInternalByRole(@RequestParam String role) {
        return ApiResponse.ok(userProfileService.listByRole(role));
    }
}
