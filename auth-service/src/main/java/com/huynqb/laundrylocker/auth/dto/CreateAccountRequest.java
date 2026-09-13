package com.huynqb.laundrylocker.auth.dto;

import java.util.Set;

public record CreateAccountRequest(
        Long userId,
        String email,
        String phoneNumber,
        String password,
        Set<String> roles) {
}
