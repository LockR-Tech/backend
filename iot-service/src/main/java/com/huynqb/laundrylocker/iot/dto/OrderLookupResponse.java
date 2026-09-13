package com.huynqb.laundrylocker.iot.dto;

import java.time.LocalDateTime;

public record OrderLookupResponse(
        Long id,
        Long userId,
        Long lockerId,
        Long sendBoxId,
        Long receiveBoxId,
        String status,
        String pinCode,
        LocalDateTime completedAt) {
}
