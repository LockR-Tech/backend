package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// createdAt/updatedAt/content thêm sau (2026-09) — chỉ bổ sung, client cũ bỏ qua được.
/// Thời gian là LocalDateTime theo múi giờ JVM (UTC trên container).
public record PaymentResponse(
        Long id,
        Long orderId,
        Long userId,
        BigDecimal amount,
        String method,
        String status,
        String referenceId,
        String referenceTransactionId,
        String paymentUrl,
        String qrCodeUrl,
        String deeplink,
        String description,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
