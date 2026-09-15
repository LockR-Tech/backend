package com.huynqb.laundrylocker.common.dto;

import java.util.Set;

public record UserSummary(
        Long id, String email, String phoneNumber, String fullName, String status, Set<String> roles, String imageUrl) {

    public UserSummary(Long id, String email, String phoneNumber, String fullName, String status) {
        this(id, email, phoneNumber, fullName, status, Set.of(), null);
    }

    public UserSummary(Long id, String email, String phoneNumber, String fullName, String status, Set<String> roles) {
        this(id, email, phoneNumber, fullName, status, roles, null);
    }
}
