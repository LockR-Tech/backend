package com.huynqb.laundrylocker.iot.dto;

import java.time.LocalDateTime;

public record DeviceStatusResponse(Long id, String deviceId, Long lockerId, String status, LocalDateTime lastSeenAt) {
}
