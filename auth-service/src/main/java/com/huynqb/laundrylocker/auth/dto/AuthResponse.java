package com.huynqb.laundrylocker.auth.dto;

import java.time.Instant;
import java.util.Set;

public record AuthResponse(
        Long accountId,
        Long userId,
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant expiresAt,
        Set<String> roles) {
}
