package com.huynqb.laundrylocker.payment.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Đơn gắn với giao dịch, ghép từ order-service; null khi giao dịch không gắn đơn hoặc không tra được.
public record OrderRef(
        Long id,
        String orderCode,
        String type,
        String serviceCategory,
        String status,
        String paymentStatus,
        BigDecimal totalPrice,
        Long lockerId,
        LocalDateTime createdAt) {
}