package com.huynqb.laundrylocker.common.dto;

import java.util.Set;

/// `language`: ngôn ngữ app người dùng chọn (`vi`/`en`, null = chưa báo ⇒ tiếng Việt) — dùng để soạn thư/SMS
/// gửi ngoài app đúng ngôn ngữ (ADR-0010).
public record UserSummary(
        Long id, String email, String phoneNumber, String fullName, String status, Set<String> roles, String imageUrl,
        String language) {

    public UserSummary(Long id, String email, String phoneNumber, String fullName, String status) {
        this(id, email, phoneNumber, fullName, status, Set.of(), null, null);
    }

    public UserSummary(Long id, String email, String phoneNumber, String fullName, String status, Set<String> roles) {
        this(id, email, phoneNumber, fullName, status, roles, null, null);
    }

    public UserSummary(
            Long id, String email, String phoneNumber, String fullName, String status, Set<String> roles,
            String imageUrl) {
        this(id, email, phoneNumber, fullName, status, roles, imageUrl, null);
    }
}
