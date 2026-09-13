package com.huynqb.laundrylocker.locker.dto;

import java.time.LocalDateTime;

public record LockerReportRatingResponse(
        Long id, Long reportId, Long userId, Integer rating, String comment, LocalDateTime createdAt) {
}
