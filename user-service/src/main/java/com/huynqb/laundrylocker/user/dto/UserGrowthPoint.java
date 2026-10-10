package com.huynqb.laundrylocker.user.dto;

/// Số người dùng mới trong một tháng (`month` dạng `YYYY-MM`, theo giờ Việt Nam).
public record UserGrowthPoint(String month, long newUsers) {
}
