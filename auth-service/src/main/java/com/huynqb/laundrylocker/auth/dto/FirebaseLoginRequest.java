package com.huynqb.laundrylocker.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record FirebaseLoginRequest(@NotBlank String idToken) {
}
