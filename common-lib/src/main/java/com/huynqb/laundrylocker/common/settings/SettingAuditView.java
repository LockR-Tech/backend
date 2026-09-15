package com.huynqb.laundrylocker.common.settings;

import java.time.LocalDateTime;

public record SettingAuditView(
        Long id,
        String key,
        String oldValue,
        String newValue,
        Long actorUserId,
        LocalDateTime changedAt) {
}
