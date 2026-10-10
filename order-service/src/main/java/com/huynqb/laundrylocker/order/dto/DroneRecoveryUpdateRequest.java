package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;

public record DroneRecoveryUpdateRequest(@NotBlank String action) {
}

