package com.huynqb.laundrylocker.order.dto;

import java.time.LocalDateTime;

public record OrderStatusResponse(
        Long orderId,
        String status,
        String statusDescription,
        String pinCode,
        Long lockerId,
        Long boxId,
        LocalDateTime estimatedReadyAt,
        Boolean isPaid,
        String nextAction,
        String paymentStatus,
        /// Đã nhận cho đơn (cộng dồn các lần chuyển khoản) và còn phải trả — để màn VietQR báo chuyển thiếu.
        java.math.BigDecimal paidAmount,
        java.math.BigDecimal amountDue) {
}
