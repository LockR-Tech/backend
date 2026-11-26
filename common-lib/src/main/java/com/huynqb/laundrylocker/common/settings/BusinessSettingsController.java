package com.huynqb.laundrylocker.common.settings;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.i18n.KeepServerText;
import com.huynqb.laundrylocker.common.i18n.ServerText;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/// API cấu hình quy tắc nghiệp vụ của service hiện tại; `{scope}` = `app.settings.scope`
/// (order, locker, payment, iot, auth, loyalty, store). Gateway chỉ cho ADMIN vào `/api/admin/**`;
/// `/api/settings/{scope}/public` không cần đăng nhập và chỉ trả quy tắc đánh dấu công khai.
///
/// Giá trị cấu hình là dữ liệu admin soạn (vd. danh sách lý do hoàn tiền) nên không qua bước dịch tự động
/// ([KeepServerText]); khi xin tiếng Anh chỉ dịch phần do code soạn của mỗi quy tắc: nhóm, tên, mô tả, đơn vị.
@RestController
@RequiredArgsConstructor
@KeepServerText
@ConditionalOnProperty(name = "app.settings.scope")
public class BusinessSettingsController {

    private final BusinessSettings settings;

    @GetMapping("/api/admin/settings/${app.settings.scope}")
    public ApiResponse<List<SettingView>> list(
            @RequestHeader(value = ServerText.LANGUAGE_HEADER, required = false) String language) {
        return ApiResponse.ok(localized(settings.list(), language));
    }

    @PutMapping("/api/admin/settings/${app.settings.scope}")
    public ApiResponse<List<SettingView>> update(
            @RequestBody UpdateSettingsRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = ServerText.LANGUAGE_HEADER, required = false) String language) {
        return ApiResponse.ok(
                "SETTINGS_UPDATED", "Business settings updated",
                localized(settings.update(request == null ? null : request.values(), userId), language));
    }

    @DeleteMapping("/api/admin/settings/${app.settings.scope}/{key}")
    public ApiResponse<List<SettingView>> reset(
            @PathVariable String key, @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = ServerText.LANGUAGE_HEADER, required = false) String language) {
        return ApiResponse.ok("SETTING_RESET", "Setting reset to default",
                localized(settings.reset(key, userId), language));
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

    /// `X-App-Language: en` ⇒ nhóm, tên, mô tả, đơn vị sang tiếng Anh; giá trị, mặc định, giới hạn giữ nguyên.
    static List<SettingView> localized(List<SettingView> views, String language) {
        if (views == null || !ServerText.wantsEnglish(language)) {
            return views;
        }
        return views.stream().map(BusinessSettingsController::toEnglish).toList();
    }

    private static SettingView toEnglish(SettingView v) {
        return new SettingView(
                v.scope(), v.key(),
                ServerText.toEnglish(v.group()), ServerText.toEnglish(v.label()),
                ServerText.toEnglish(v.description()),
                v.type(), v.value(), v.defaultValue(), v.overridden(), v.min(), v.max(),
                ServerText.toEnglish(v.unit()),
                v.allowedValues(), v.allowedItems(), v.publicValue(), v.updatedByUserId(), v.updatedAt());
    }

    public record UpdateSettingsRequest(Map<String, Object> values) {
    }
}
