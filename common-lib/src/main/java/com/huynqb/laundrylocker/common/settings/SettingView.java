package com.huynqb.laundrylocker.common.settings;

import java.time.LocalDateTime;
import java.util.List;

/// Một quy tắc như admin thấy: giá trị đang áp dụng, mặc định, giới hạn và ai sửa lần cuối.
public record SettingView(
        String scope,
        String key,
        String group,
        String label,
        String description,
        SettingType type,
        String value,
        String defaultValue,
        boolean overridden,
        String min,
        String max,
        String unit,
        List<String> allowedValues,
        boolean publicValue,
        Long updatedByUserId,
        LocalDateTime updatedAt) {
}
