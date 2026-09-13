package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DroneUnitRequest(@NotNull Long lockerId, @NotBlank String code) {
}
