package com.huynqb.laundrylocker.user.dto;

/// Body của `PUT /api/user/onboarding/auto-show`: false = "Bỏ qua vĩnh viễn", true = bật tự mở lại.
public record OnboardingAutoShowRequest(Boolean autoShow) {
}
