package com.huynqb.laundrylocker.common.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/// Ghi {@link RequestActor} từ header gateway cho suốt request, xoá khi xong để luồng sau không dính người cũ.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestActorFilter extends OncePerRequestFilter {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String ROLES_HEADER = "X-User-Roles";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Long userId = parseId(request.getHeader(USER_ID_HEADER));
        String roles = request.getHeader(ROLES_HEADER);
        if (userId == null && (roles == null || roles.isBlank())) {
            RequestActor.clear();
        } else {
            RequestActor.set(new RequestActor(userId, roles == null || roles.isBlank() ? null : roles.trim()));
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestActor.clear();
        }
    }

    static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
