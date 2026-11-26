package com.huynqb.laundrylocker.notification.dto;

import java.util.List;

/// Xoá mềm nhiều thông báo một lần: theo `ids` đã tích, hoặc `all = true` ⇒ mọi thông báo khớp bộ lọc
/// (`status`, `type`, `search`, `recipientIds` — giống bảng). `reason` bắt buộc (ADR-0009).
public record AdminNotificationBulkDeleteRequest(
        List<Long> ids,
        Boolean all,
        String status,
        String type,
        String search,
        List<Long> recipientIds,
        String reason) {
}
