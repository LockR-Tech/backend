package com.huynqb.laundrylocker.common.settings;

import java.util.List;

/// Khai báo một quy tắc nghiệp vụ admin chỉnh được.
///
/// `key` trùng tên property Spring cũ (ví dụ `app.order.send-base-fee`) để biến môi trường
/// đang đặt trên VM vẫn là giá trị mặc định; giá trị admin lưu trong DB được ưu tiên hơn.
/// `publicValue` = true ⇒ app/web không đăng nhập được đọc (giá, giới hạn hiển thị cho khách).
/// `allowedValues` = chọn đúng một giá trị trong danh sách; `allowedItems` (chỉ STRING) = chuỗi nhiều
/// phần tử cách nhau bởi dấu phẩy, mỗi phần tử thuộc danh sách — xem {@link #stringList}.
public record SettingDefinition(
        String key,
        String group,
        String label,
        String description,
        SettingType type,
        String defaultValue,
        String min,
        String max,
        String unit,
        List<String> allowedValues,
        List<String> allowedItems,
        boolean publicValue) {

    public static SettingDefinition integer(
            String key, String group, String label, String description, int defaultValue, Integer min, Integer max, String unit) {
        return new SettingDefinition(key, group, label, description, SettingType.INTEGER, String.valueOf(defaultValue),
                min == null ? null : String.valueOf(min), max == null ? null : String.valueOf(max), unit, List.of(), List.of(), false);
    }

    public static SettingDefinition decimal(
            String key, String group, String label, String description, String defaultValue, String min, String max, String unit) {
        return new SettingDefinition(key, group, label, description, SettingType.DECIMAL, defaultValue, min, max, unit, List.of(), List.of(), false);
    }

    public static SettingDefinition bool(String key, String group, String label, String description, boolean defaultValue) {
        return new SettingDefinition(key, group, label, description, SettingType.BOOLEAN, String.valueOf(defaultValue),
                null, null, null, List.of(), List.of(), false);
    }

    public static SettingDefinition string(
            String key, String group, String label, String description, String defaultValue, List<String> allowedValues) {
        return new SettingDefinition(key, group, label, description, SettingType.STRING, defaultValue,
                null, null, null, allowedValues == null ? List.of() : List.copyOf(allowedValues), List.of(), false);
    }

    /// Chuỗi danh sách cách nhau bởi dấu phẩy (ví dụ "CASH,WALLET"), mỗi phần tử phải thuộc `allowedItems`
    /// (không phân biệt hoa thường, lưu đúng cách viết trong danh sách, bỏ trùng). Admin ghi phần tử lạ ⇒
    /// `400 SETTING_INVALID`. Giá trị đã lưu từ trước còn phần tử nay không hỗ trợ thì khi đọc chỉ bỏ các
    /// phần tử đó, giữ phần còn lại. Danh sách rỗng được phép.
    public static SettingDefinition stringList(
            String key, String group, String label, String description, String defaultValue, List<String> allowedItems) {
        if (allowedItems == null || allowedItems.isEmpty()) {
            throw new IllegalArgumentException("stringList " + key + " needs allowed items");
        }
        return new SettingDefinition(key, group, label, description, SettingType.STRING, defaultValue,
                null, null, null, List.of(), List.copyOf(allowedItems), false);
    }

    public static SettingDefinition integerList(
            String key, String group, String label, String description, String defaultValue, Integer min, Integer max, String unit) {
        return new SettingDefinition(key, group, label, description, SettingType.INTEGER_LIST, defaultValue,
                min == null ? null : String.valueOf(min), max == null ? null : String.valueOf(max), unit, List.of(), List.of(), false);
    }

    /// Cho phép đọc không cần đăng nhập qua `/api/settings/{scope}/public`.
    public SettingDefinition asPublic() {
        return new SettingDefinition(
                key, group, label, description, type, defaultValue, min, max, unit, allowedValues, allowedItems, true);
    }
}
