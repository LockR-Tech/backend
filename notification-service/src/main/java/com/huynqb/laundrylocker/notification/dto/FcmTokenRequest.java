package com.huynqb.laundrylocker.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FcmTokenRequest(@NotNull Long userId, @NotBlank String token, String deviceType) {
}
