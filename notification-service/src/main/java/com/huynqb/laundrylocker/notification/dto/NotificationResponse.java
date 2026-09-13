package com.huynqb.laundrylocker.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long userId,
        String title,
        String message,
        String type,
        Boolean isRead,
        Long referenceId,
        String referenceType,
        String status,
        LocalDateTime readAt,
        LocalDateTime createdAt) {
}
