package com.huynqb.laundrylocker.user.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.user.client.AuthClient;
import com.huynqb.laundrylocker.user.client.NotificationClient;
import com.huynqb.laundrylocker.user.dto.AdminCreateUserRequest;
import com.huynqb.laundrylocker.user.dto.UserProfileRequest;
import com.huynqb.laundrylocker.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final AuthClient authClient;
    private final NotificationClient notificationClient;

    @PostMapping("/api/users")
    public ApiResponse<UserSummary> create(@RequestBody UserProfileRequest request) {
        return ApiResponse.ok("USER_CREATED", "User created", userProfileService.create(request));
    }

    @PostMapping("/api/user")
    public ApiResponse<UserSummary> createLegacy(@RequestBody UserProfileRequest request) {
        return create(request);
    }

    @PutMapping("/api/users/{id}")
    public ApiResponse<UserSummary> update(@PathVariable Long id, @RequestBody UserProfileRequest request) {
        return ApiResponse.ok("USER_UPDATED", "User updated", userProfileService.update(id, request));
    }

    @GetMapping("/api/users/{id}")
    public ApiResponse<UserSummary> get(@PathVariable Long id) {
        return ApiResponse.ok(userProfileService.get(id));
    }

    @GetMapping("/api/user/{id}")
    public ApiResponse<UserSummary> getLegacy(@PathVariable Long id) {
        return get(id);
    }

    @GetMapping("/api/users")
    public ApiResponse<List<UserSummary>> list() {
        return ApiResponse.ok(userProfileService.list());
    }

    @GetMapping("/api/user/read")
    public ApiResponse<List<UserSummary>> listLegacy() {
        return list();
    }

    @GetMapping("/api/user/profile")
    public ApiResponse<UserSummary> profile(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("USER_PROFILE_OK", "User profile", userProfileService.get(userId));
    }

    @PutMapping("/api/user/profile")
    public ApiResponse<UserSummary> updateProfile(
            @RequestHeader("X-User-Id") Long userId, @RequestBody UserProfileRequest request) {
        return ApiResponse.ok("PROFILE_UPDATED", "Profile updated", userProfileService.update(userId, request));
    }

    @PutMapping("/api/user/avatar")
    public ApiResponse<UserSummary> updateAvatar(
            @RequestHeader("X-User-Id") Long userId, @RequestBody Map<String, Object> request) {
        String imageUrl =
                request.get("imageUrl") == null ? String.valueOf(request.get("avatar")) : String.valueOf(request.get("imageUrl"));
        return ApiResponse.ok("AVATAR_UPDATED", "Avatar updated", userProfileService.updateAvatar(userId, imageUrl));
    }

    @PutMapping("/api/user/password")
    public ApiResponse<Void> changePassword(
            @RequestHeader("X-User-Id") Long userId, @RequestBody Map<String, Object> request) {
        authClient.changePassword(userId, request);
        return ApiResponse.ok("PASSWORD_CHANGED", "Password changed successfully");
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
    public ApiResponse<List<com.huynqb.laundrylocker.user.dto.AdminUserView>> adminUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role) {
        List<com.huynqb.laundrylocker.user.dto.AdminUserView> views =
                userProfileService.listAdminViews(search, status, role);
        // Best-effort enrich with auth provider/email-verified (never break the list on lookup failure).
        try {
            List<Long> ids = views.stream().map(com.huynqb.laundrylocker.user.dto.AdminUserView::id).toList();
            if (!ids.isEmpty()) {
                Map<Long, Map<String, Object>> byUser =
                        authClient.accountsByUsers(ids).data().stream()
                                .filter(m -> m.get("userId") != null)
                                .collect(
                                        java.util.stream.Collectors.toMap(
                                                m -> Long.valueOf(m.get("userId").toString()), m -> m, (a, b) -> a));
                views =
                        views.stream()
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
            }
        } catch (Exception ignored) {
            // auth-service unavailable -> return profile-only views
        }
        return ApiResponse.ok(views);
    }

    @PostMapping("/api/admin/users")
    public ApiResponse<UserSummary> adminCreate(@RequestBody AdminCreateUserRequest request) {
        UserProfileRequest profileRequest = new UserProfileRequest(
                request.email(),
                request.phoneNumber(),
                request.firstName(),
                request.lastName(),
                request.birthday(),
                request.imageUrl(),
                request.status(),
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
            authClient.createAccount(payload);
        } catch (Exception ex) {
            userProfileService.delete(user.id());
            throw new com.huynqb.laundrylocker.common.exception.BusinessException("ACCOUNT_CREATION_FAILED", "Failed to create auth account: " + ex.getMessage());
        }
        return ApiResponse.ok("USER_CREATED", "User created", user);
    }

    @GetMapping("/api/admin/users/{id}")
    public ApiResponse<UserSummary> adminGet(@PathVariable Long id) {
        return get(id);
    }

    @PutMapping("/api/admin/users/{id}")
    public ApiResponse<UserSummary> adminUpdate(@PathVariable Long id, @RequestBody UserProfileRequest request) {
        return update(id, request);
    }

    @PutMapping("/api/admin/users/{id}/status")
    public ApiResponse<UserSummary> adminStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, Object> request) {
        String resolved = status != null ? status : String.valueOf(request == null ? "ACTIVE" : request.get("status"));
        return ApiResponse.ok("USER_STATUS_UPDATED", "User status updated", userProfileService.updateStatus(id, resolved));
    }

    @PutMapping("/api/admin/users/{id}/roles")
    public ApiResponse<UserSummary> adminRoles(@PathVariable Long id, @RequestBody Map<String, Set<String>> request) {
        return ApiResponse.ok("USER_ROLES_UPDATED", "User roles updated", userProfileService.updateRoles(id, request.get("roles")));
    }

    @DeleteMapping("/api/admin/users/{id}")
    public ApiResponse<Void> adminDelete(@PathVariable Long id) {
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
