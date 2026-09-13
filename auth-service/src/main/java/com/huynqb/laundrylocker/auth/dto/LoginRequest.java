package com.huynqb.laundrylocker.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {
}
