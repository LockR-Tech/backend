package com.huynqb.laundrylocker.common.settings;

/// Kiểu giá trị của một quy tắc nghiệp vụ — quyết định cách kiểm tra và ép kiểu.
public enum SettingType {
    INTEGER,
    DECIMAL,
    BOOLEAN,
    STRING,
    /// Danh sách số nguyên, lưu dạng "2,4,8".
    INTEGER_LIST
}
