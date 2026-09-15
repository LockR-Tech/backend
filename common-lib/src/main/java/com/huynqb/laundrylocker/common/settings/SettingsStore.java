package com.huynqb.laundrylocker.common.settings;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/// Nơi lưu giá trị admin đã ghi đè — mặc định là bảng `system_settings` trong schema của service.
public interface SettingsStore {

    record StoredSetting(String key, String value, Long updatedByUserId, LocalDateTime updatedAt) {
    }

    Map<String, StoredSetting> loadAll();

    void upsert(String key, String value, Long actorUserId);

    void delete(String key);

    void audit(String key, String oldValue, String newValue, Long actorUserId);

    List<SettingAuditView> audits(String key, int limit);
}
