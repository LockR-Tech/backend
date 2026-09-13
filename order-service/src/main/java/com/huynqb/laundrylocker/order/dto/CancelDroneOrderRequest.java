package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CancelDroneOrderRequest(
        @NotNull @Min(1) @Max(5) Integer reasonCode,
        String note) {
}
