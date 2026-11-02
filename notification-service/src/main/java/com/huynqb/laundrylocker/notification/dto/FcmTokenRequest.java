package com.huynqb.laundrylocker.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/// `language` = ngôn ngữ app (`vi`/`en`); null khi app cũ hoặc user-service gọi nội bộ ⇒ giữ giá trị đã lưu.
public record FcmTokenRequest(@NotNull Long userId, @NotBlank String token, String deviceType, String language) {

    public FcmTokenRequest(Long userId, String token, String deviceType) {
        this(userId, token, deviceType, null);
    }
}
