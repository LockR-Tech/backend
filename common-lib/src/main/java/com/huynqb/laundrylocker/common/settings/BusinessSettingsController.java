package com.huynqb.laundrylocker.common.settings;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/// API cấu hình quy tắc nghiệp vụ của service hiện tại; `{scope}` = `app.settings.scope`
/// (order, locker, payment, iot, auth, loyalty, store). Gateway chỉ cho ADMIN vào `/api/admin/**`;
/// `/api/settings/{scope}/public` không cần đăng nhập và chỉ trả quy tắc đánh dấu công khai.
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.settings.scope")
public class BusinessSettingsController {

    private final BusinessSettings settings;

    @GetMapping("/api/admin/settings/${app.settings.scope}")
    public ApiResponse<List<SettingView>> list() {
        return ApiResponse.ok(settings.list());
    }

    @PutMapping("/api/admin/settings/${app.settings.scope}")
    public ApiResponse<List<SettingView>> update(
            @RequestBody UpdateSettingsRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(
                "SETTINGS_UPDATED", "Business settings updated",
                settings.update(request == null ? null : request.values(), userId));
    }

    @DeleteMapping("/api/admin/settings/${app.settings.scope}/{key}")
    public ApiResponse<List<SettingView>> reset(
            @PathVariable String key, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("SETTING_RESET", "Setting reset to default", settings.reset(key, userId));
    }

    @GetMapping("/api/admin/settings/${app.settings.scope}/audits")
    public ApiResponse<List<SettingAuditView>> audits(
            @RequestParam(required = false) String key,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        return ApiResponse.ok(settings.audits(key, limit));
    }

    @GetMapping("/api/settings/${app.settings.scope}/public")
    public ApiResponse<Map<String, Object>> publicValues() {
        return ApiResponse.ok(settings.publicValues());
    }

    public record UpdateSettingsRequest(Map<String, Object> values) {
    }
}
