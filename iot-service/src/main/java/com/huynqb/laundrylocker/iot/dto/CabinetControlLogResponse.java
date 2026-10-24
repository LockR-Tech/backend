package com.huynqb.laundrylocker.iot.dto;

import java.time.LocalDateTime;

public record CabinetControlLogResponse(
        Long id,
        Long lockerId,
        String source,
        Long actorUserId,
        String action,
        Integer axis,
        Long boxId,
        Integer boxNumber,
        Double revolutions,
        String status,
        String message,
        LocalDateTime occurredAt,
        LocalDateTime createdAt) {
}
