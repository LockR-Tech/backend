package com.huynqb.laundrylocker.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record RegisterRequest(
        Long userId,
        String email,
        String phoneNumber,
        String firstName,
        String lastName,
        Set<String> roles,
        @NotBlank String password) {
}
