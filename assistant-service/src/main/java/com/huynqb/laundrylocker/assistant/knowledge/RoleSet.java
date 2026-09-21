package com.huynqb.laundrylocker.assistant.knowledge;

import com.huynqb.laundrylocker.common.exception.BusinessException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Vai trò được đọc một tài liệu. `ALL` = mọi người dùng đã đăng nhập.
public final class RoleSet {

    public static final String ALL = "ALL";
    public static final String ADMIN = "ADMIN";
    public static final Set<String> ASSIGNABLE =
            Set.of(ALL, "CUSTOMER", "LOCKER_TECHNICIAN", "DRONE_TECHNICIAN", ADMIN);

    private RoleSet() {
    }

    /// Chuẩn hoá vai trò admin chọn cho tài liệu; rỗng ⇒ ALL, có ALL thì bỏ các vai trò khác.
    public static List<String> normalizeAllowed(Collection<String> roles) {
        Set<String> normalized = new LinkedHashSet<>();
        if (roles != null) {
            for (String role : roles) {
                if (role == null || role.isBlank()) {
                    continue;
                }
                for (String part : role.split(",")) {
                    String value = part.trim().toUpperCase(Locale.ROOT);
                    if (value.isEmpty()) {
                        continue;
                    }
                    if (!ASSIGNABLE.contains(value)) {
                        throw new BusinessException("ROLE_INVALID", "Vai trò không hợp lệ: " + value);
                    }
                    normalized.add(value);
                }
            }
        }
        if (normalized.isEmpty() || normalized.contains(ALL)) {
            return List.of(ALL);
        }
        return List.copyOf(normalized);
    }

    /// Vai trò người hỏi (+ ALL) để lọc tài liệu; chỉ giữ ký tự [A-Z_] vì ghép vào literal mảng SQL.
    public static List<String> readerRoles(Collection<String> userRoles) {
        Set<String> roles = new LinkedHashSet<>();
        roles.add(ALL);
        if (userRoles != null) {
            for (String role : userRoles) {
                String value = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
                if (value.startsWith("ROLE_")) {
                    value = value.substring(5);
                }
                if (value.matches("[A-Z_]+")) {
                    roles.add(value);
                }
            }
        }
        return List.copyOf(roles);
    }

    /// Literal mảng Postgres `{A,B}`; phần tử đã được kiểm chỉ gồm [A-Z_].
    static String toArrayLiteral(Collection<String> roles) {
        for (String role : roles) {
            if (!role.matches("[A-Z_]+")) {
                throw new IllegalArgumentException("Unsafe role: " + role);
            }
        }
        return "{" + String.join(",", roles) + "}";
    }
}
