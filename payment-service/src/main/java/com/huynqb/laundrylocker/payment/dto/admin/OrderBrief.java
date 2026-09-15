package com.huynqb.laundrylocker.payment.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Bản sao OrderBriefResponse của order-service (/internal/orders/batch).
public record OrderBrief(
        Long id,
        String orderCode,
        Long userId,
        String type,
        String serviceCategory,
        String status,
        String paymentStatus,
        String deliveryStage,
        Long lockerId,
        Long destinationLockerId,
        Long storeId,
        BigDecimal totalPrice,
        BigDecimal extraFee,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime paidAt) {
}