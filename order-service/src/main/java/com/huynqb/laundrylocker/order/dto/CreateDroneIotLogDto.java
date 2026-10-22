package com.huynqb.laundrylocker.order.dto;

public record CreateDroneIotLogDto(
        Long droneId,
        String droneCode,
        String component,
        String eventType,
        String result,
        String actorType,
        String actorId,
        Long orderId,
        String message
) {}

