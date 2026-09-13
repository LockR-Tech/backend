package com.huynqb.laundrylocker.iot.dto;

import java.time.LocalDateTime;

public record BoxAccessLogResponse(
        Long id,
        Long boxId,
        Long lockerId,
        Long orderId,
        Long actorUserId,
        String credentialType,
        String result,
        String message,
        LocalDateTime createdAt) {
}
