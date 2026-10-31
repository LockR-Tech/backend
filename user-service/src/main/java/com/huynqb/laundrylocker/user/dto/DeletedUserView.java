package com.huynqb.laundrylocker.user.dto;

import java.time.LocalDateTime;

public record DeletedUserView(
        Long id,
        String email,
        String phoneNumber,
        String fullName,
        LocalDateTime deletedAt,
        String deletedReason) {
}
