package com.huynqb.laundrylocker.payment.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Giao dịch cho trang admin: mọi field PaymentResponse + kind, paidAt, tổng đã hoàn,
/// đơn và khách ghép từ service khác. kind = ORDER | TOPUP (nạp ví, orderId = 0).
/// paidAt = updatedAt khi status COMPLETED (bảng payments không có cột paid_at).
public record AdminPaymentResponse(
        Long id,
        Long orderId,
        Long userId,
        BigDecimal amount,
        String method,
        String status,
        String kind,
        String referenceId,
        String referenceTransactionId,
        String paymentUrl,
        String qrCodeUrl,
        String deeplink,
        String description,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime paidAt,
        BigDecimal refundedAmount,
        OrderRef order,
        PersonRef customer) {
}