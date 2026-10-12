package com.huynqb.laundrylocker.user.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.Set;

/// `fullName` chỉ để tương thích app cũ (gửi một ô họ tên): khi không có firstName/lastName thì được tách
/// thành firstName = phần trước khoảng trắng cuối, lastName = từ cuối — ghép lại vẫn ra đúng họ tên cũ.
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfileRequest(
        String email,
        String phoneNumber,
        String firstName,
        String lastName,
        LocalDate birthday,
        String imageUrl,
        String status,
        Set<String> roles,
        String fullName) {

    /// JSON luôn đi qua constructor đầy đủ (có thêm constructor 8 tham số cho code cũ).
    @JsonCreator
    public UserProfileRequest {
    }

    public UserProfileRequest(
            String email,
            String phoneNumber,
            String firstName,
            String lastName,
            LocalDate birthday,
            String imageUrl,
            String status,
            Set<String> roles) {
        this(email, phoneNumber, firstName, lastName, birthday, imageUrl, status, roles, null);
    }
}
