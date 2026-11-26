package com.huynqb.laundrylocker.notification.dto;

import com.huynqb.laundrylocker.notification.model.NotificationMessage;

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

    public static NotificationResponse from(NotificationMessage n) {
        return new NotificationResponse(
                n.getId(), n.getUserId(), n.getTitle(), n.getMessage(), n.getType(), n.getIsRead(),
                n.getReferenceId(), n.getReferenceType(), n.getStatus(), n.getReadAt(), n.getCreatedAt());
    }
}
