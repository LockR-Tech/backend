package com.huynqb.laundrylocker.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
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
