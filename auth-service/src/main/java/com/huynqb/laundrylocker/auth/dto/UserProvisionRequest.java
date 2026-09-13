package com.huynqb.laundrylocker.auth.dto;

import java.util.Set;

public record UserProvisionRequest(
        String email,
        String phoneNumber,
        String firstName,
        String lastName,
        String status,
        Set<String> roles) {
}
