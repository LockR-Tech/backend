package com.huynqb.laundrylocker.user.dto;

import java.time.LocalDateTime;
import java.util.Map;

/// Trạng thái hướng dẫn lần đầu của người đang đăng nhập. `updatedAt` = null khi chưa từng ghi.
public record OnboardingStateView(boolean autoShow, Map<String, Integer> seenGroups, LocalDateTime updatedAt) {
}
