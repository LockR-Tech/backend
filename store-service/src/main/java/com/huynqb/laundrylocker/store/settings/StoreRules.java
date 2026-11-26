package com.huynqb.laundrylocker.store.settings;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.huynqb.laundrylocker.store.settings.StoreSettingsCatalog.NEARBY_DEFAULT_RADIUS_KM;

/// Đọc có kiểu các quy tắc nghiệp vụ của store-service. Mỗi lần gọi lấy giá trị đang áp dụng
/// (admin sửa có hiệu lực tối đa sau 30 giây, ngay lập tức trên instance nhận lệnh sửa).
@Component
@RequiredArgsConstructor
public class StoreRules {

    private final BusinessSettings settings;

    public double nearbyDefaultRadiusKm() {
        return settings.getDecimal(NEARBY_DEFAULT_RADIUS_KM).doubleValue();
    }

    /// Điểm gửi demo đang ẩn với người này (F3-G14). Điểm gửi không nằm trong danh sách demo luôn hiện.
    public boolean demoStoreHiddenFor(Long storeId, Long userId, String roles) {
        if (storeId == null || settings.getBoolean(StoreSettingsCatalog.DEMO_VISIBLE)) {
            return false;
        }
        if (!settings.getIntList(StoreSettingsCatalog.DEMO_STORE_IDS).contains(storeId.intValue())) {
            return false;
        }
        if (roles != null && java.util.Arrays.stream(roles.split(","))
                .map(String::trim).anyMatch(r -> r.equalsIgnoreCase("ADMIN") || r.equalsIgnoreCase("ROLE_ADMIN"))) {
            return false;
        }
        return userId == null || userId <= 0
                || !settings.getIntList(StoreSettingsCatalog.DEMO_ALLOWED_USER_IDS).contains(userId.intValue());
    }
}
