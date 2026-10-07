package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/// Số đo drone tự báo qua telemetry (order-service chuyển tiếp), tra theo mã drone.
public record DroneTelemetryRequest(
        @NotBlank String code,
        @NotNull @Min(0) @Max(100) Integer batteryPercent) {
}
