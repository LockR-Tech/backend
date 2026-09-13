package com.huynqb.laundrylocker.common.dto;

public record NotificationRequest(
        Long userId, String title, String message, String type, Long referenceId, String referenceType) {
}
