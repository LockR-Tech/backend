package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DroneBatteryRequest(@NotNull @Min(0) @Max(100) Integer batteryPercent) {
}
