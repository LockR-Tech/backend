package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExtendSlaRequest(
        @NotNull(message = "extensionHours is required")
        @Min(value = 1, message = "extensionHours must be at least 1")
        Integer extensionHours,

        @Size(max = 1000)
        String reason) {
}
