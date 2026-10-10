package com.huynqb.laundrylocker.user.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Rich user row for the admin web (profile + auth provider/verification).
 * `provider`/`emailVerified` null khi auth-service không tra được.
 */
public record AdminUserView(
        Long id,
        String email,
        String phoneNumber,
        String fullName,
        String status,
        Set<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String provider,
        Boolean emailVerified,
        String imageUrl) {

    public AdminUserView withAuth(String provider, Boolean emailVerified) {
        return new AdminUserView(
                id, email, phoneNumber, fullName, status, roles, createdAt, updatedAt, provider, emailVerified,
                imageUrl);
    }
}
