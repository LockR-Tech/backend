package com.huynqb.laundrylocker.iot.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceStatusRequest(@NotBlank String deviceId, Long lockerId, @NotBlank String status) {
}
