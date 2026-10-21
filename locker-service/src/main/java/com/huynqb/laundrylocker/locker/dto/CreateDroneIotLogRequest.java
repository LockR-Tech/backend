package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDroneIotLogRequest(
        @NotNull Long droneId,
        String droneCode,
        @NotBlank String component,
        @NotBlank String eventType,
        @NotBlank String result,
        String actorType,
        String actorId,
        Long orderId,
        @NotBlank String message
) {}
