package com.huynqb.laundrylocker.notification.dto;

/// Bản sao OrderParticipantsResponse của order-service (`/internal/orders/{id}/participants`).
public record OrderParticipants(Long orderId, Long userId, Long receiverUserId) {
}
