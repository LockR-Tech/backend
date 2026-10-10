package com.huynqb.laundrylocker.auth.dto;

/// user-service gọi khi admin khoá/mở người dùng: `ACTIVE` hoặc `INACTIVE`.
public record AccountStatusRequest(String status) {
}
