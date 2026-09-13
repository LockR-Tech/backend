package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotBlank;

public record DroneStatusRequest(@NotBlank String status, String reason) {
}
