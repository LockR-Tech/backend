package com.huynqb.laundrylocker.iot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BleTestGrantRequest(@NotNull @Positive Long userId) {
}
