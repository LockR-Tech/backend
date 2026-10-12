package com.huynqb.laundrylocker.loyalty.dto;

import jakarta.validation.constraints.NotNull;

/// `userId` trong body bị bỏ qua ở đường của khách (lấy từ X-User-Id); giữ field để body cũ vẫn hợp lệ.
public record RedeemStampRequest(Long userId, @NotNull Integer stamps, String rewardName) {
}
