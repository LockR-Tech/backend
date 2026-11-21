package com.huynqb.laundrylocker.user.dto;

/// Body của `POST /api/user/onboarding/seen`: đã xem xong nhóm `group` ở phiên bản `version`.
public record OnboardingSeenRequest(String group, Integer version) {
}
