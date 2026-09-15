package com.huynqb.laundrylocker.payment.dto.admin;

/// Khách/nhân viên ghép từ user-service; null khi không tra được.
public record PersonRef(Long id, String fullName, String phoneNumber, String email) {
}