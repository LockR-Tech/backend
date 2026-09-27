package com.huynqb.laundrylocker.iot.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/// Bộ điều khiển tủ cho màn admin (ADR-0008). `online` = thấy heartbeat/discovery trong 150 s.
/// `setupResult` là nguyên văn `iot/{mac}/setup/result` gần nhất (kết quả thử từng ô).
public record GatewayDeviceResponse(
        Long id,
        String macAddress,
        String hardware,
        String firmwareVersion,
        Integer slaveId,
        Integer availableSlots,
        Long reportedLockerId,
        Long lockerId,
        boolean online,
        String setupStatus,
        String setupProgress,
        JsonNode setupResult,
        LocalDateTime setupRequestedAt,
        LocalDateTime setupFinishedAt,
        LocalDateTime lastSeenAt) {
}
