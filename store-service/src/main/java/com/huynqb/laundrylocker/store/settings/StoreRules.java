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
}
