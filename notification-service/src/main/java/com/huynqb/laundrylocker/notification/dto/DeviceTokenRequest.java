package com.huynqb.laundrylocker.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceTokenRequest(@NotBlank String token, String deviceType) {
}
