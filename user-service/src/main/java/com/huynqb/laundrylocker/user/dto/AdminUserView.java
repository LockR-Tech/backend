package com.huynqb.laundrylocker.user.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Rich user row for the admin web (profile + auth provider/verification).
 */
public record AdminUserView(
        Long id,
        String email,
        String phoneNumber,
        String fullName,
        String status,
        Set<String> roles,
        LocalDateTime createdAt,
        String provider,
        Boolean emailVerified,
        String imageUrl) {

    public AdminUserView withAuth(String provider, Boolean emailVerified) {
        return new AdminUserView(
                id, email, phoneNumber, fullName, status, roles, createdAt, provider, emailVerified, imageUrl);
    }
}
