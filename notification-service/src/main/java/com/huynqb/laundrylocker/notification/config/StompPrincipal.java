package com.huynqb.laundrylocker.notification.config;

import java.security.Principal;
import java.util.List;

/// Người dùng của một phiên WebSocket: `name` là subject của JWT (id người dùng), kèm vai trò để
/// kiểm quyền đăng ký topic mà không phải đọc lại token.
public record StompPrincipal(String name, List<String> roles) implements Principal {

    public StompPrincipal {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean hasAnyRole(String... wanted) {
        for (String role : wanted) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
