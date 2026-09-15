package com.huynqb.laundrylocker.order.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Thông tin rút gọn của đơn cho service khác (payment-service ghép mã đơn, loại đơn).
public record OrderBriefResponse(
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
