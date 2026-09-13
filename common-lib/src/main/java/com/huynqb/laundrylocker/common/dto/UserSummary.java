package com.huynqb.laundrylocker.common.dto;

import java.util.Set;

public record UserSummary(
        Long id, String email, String phoneNumber, String fullName, String status, Set<String> roles) {

    public UserSummary(Long id, String email, String phoneNumber, String fullName, String status) {
        this(id, email, phoneNumber, fullName, status, Set.of());
    }
}
