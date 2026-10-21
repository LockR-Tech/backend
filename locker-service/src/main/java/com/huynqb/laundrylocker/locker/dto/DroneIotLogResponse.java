package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

public record DroneIotLogResponse(
        Long id,
        Long droneId,
        String droneCode,
        String component,
        String eventType,
        String result,
        String actorType,
        String actorId,
        Long orderId,
        String message,
        LocalDateTime createdAt
) {}
