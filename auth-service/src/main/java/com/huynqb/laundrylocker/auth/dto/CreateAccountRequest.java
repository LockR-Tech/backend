package com.huynqb.laundrylocker.auth.dto;

import java.util.Set;

/// `status` null/rỗng = ACTIVE; admin tạo sẵn người dùng bị khoá thì gửi INACTIVE.
public record CreateAccountRequest(
        Long userId,
        String email,
        String phoneNumber,
        String password,
        Set<String> roles,
        String status) {
}
