package com.huynqb.laundrylocker.auth.dto;

/// user-service gọi khi admin đổi email/số điện thoại của người dùng. Field null hoặc rỗng = giữ nguyên.
public record AccountIdentifiersRequest(String email, String phoneNumber) {
}
