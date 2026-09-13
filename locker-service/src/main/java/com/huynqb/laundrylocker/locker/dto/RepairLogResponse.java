package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

public record RepairLogResponse(
        Long id, Long reportId, Long actorUserId, String note, LocalDateTime createdAt) {
}
