package com.huynqb.laundrylocker.common.security;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/// Đọc header `X-User-Roles` (gateway gắn sau khi kiểm JWT, dạng `ADMIN,TECHNICIAN`).
public final class UserRoles {

    private UserRoles() {
    }

    public static List<String> parse(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return Arrays.stream(header.split(","))
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .filter(role -> !role.isEmpty())
                .toList();
    }

    public static boolean isAdmin(String header) {
        return parse(header).contains("ADMIN");
    }
}
