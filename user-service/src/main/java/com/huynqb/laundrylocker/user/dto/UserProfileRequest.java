package com.huynqb.laundrylocker.user.dto;

import java.time.LocalDate;
import java.util.Set;

public record UserProfileRequest(
        String email,
        String phoneNumber,
        String firstName,
        String lastName,
        LocalDate birthday,
        String imageUrl,
        String status,
        Set<String> roles) {
}
