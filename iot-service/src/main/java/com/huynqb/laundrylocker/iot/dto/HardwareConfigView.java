package com.huynqb.laundrylocker.iot.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/// Cấu hình phần cứng một tủ trên tab "Cấu hình phần cứng". `defaultTemplate = true` ⇒ tủ chưa lưu
/// cấu hình nào, đang hiện mẫu tủ TU01. Cấu hình đang chạy thật trên tủ lấy từ `panel` (trường `hardware`).
public record HardwareConfigView(
        Long lockerId,
        JsonNode config,
        JsonNode wiring,
        boolean defaultTemplate,
        Long updatedBy,
        LocalDateTime updatedAt,
        Long appliedBy,
        LocalDateTime appliedAt,
        String applyStatus,
        String applyMessage) {
}
