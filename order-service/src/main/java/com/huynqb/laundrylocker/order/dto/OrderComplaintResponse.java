package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

public record OrderComplaintResponse(
        Long id, Long orderId, Long userId, String type, String description, String status, LocalDateTime createdAt) {
}
