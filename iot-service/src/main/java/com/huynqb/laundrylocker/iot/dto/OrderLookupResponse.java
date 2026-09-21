package com.huynqb.laundrylocker.iot.dto;

import java.time.LocalDateTime;

/// Phần của OrderResponse (order-service) mà iot-service cần để quyết định mở ô.
public record OrderLookupResponse(
        Long id,
        Long userId,
        Long lockerId,
        Long sendBoxId,
        Long receiveBoxId,
        String status,
        String pinCode,
        LocalDateTime completedAt,
        String type,
        String paymentStatus,
        LocalDateTime pickupDeadline,
        // Mã đúng nhưng tạm chưa được mở ô (null = mở được) — order-service quyết định.
        String accessBlockReason) {

    public OrderLookupResponse(
            Long id,
            Long userId,
            Long lockerId,
            Long sendBoxId,
            Long receiveBoxId,
            String status,
            String pinCode,
            LocalDateTime completedAt,
            String type) {
        this(id, userId, lockerId, sendBoxId, receiveBoxId, status, pinCode, completedAt, type, null, null, null);
    }

    public OrderLookupResponse(
            Long id,
            Long userId,
            Long lockerId,
            Long sendBoxId,
            Long receiveBoxId,
            String status,
            String pinCode,
            LocalDateTime completedAt) {
        this(id, userId, lockerId, sendBoxId, receiveBoxId, status, pinCode, completedAt, null);
    }
}
