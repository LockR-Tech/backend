package com.huynqb.laundrylocker.payment.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Biến động ví cho trang admin: mọi field WalletTransactionResponse + walletId, userId, khách.
/// relatedOrderId/orderCode: chỉ có giá trị khi source = ORDER_PAYMENT (xem WalletTransactionRefs).
/// Cùng cách suy ra orderCode với WalletTransactionResponse (API khách hàng) — admin và app
/// phải luôn hiện cùng một mã đơn cho cùng một biến động, không tự suy diễn riêng mỗi bên.
public record AdminWalletTransactionResponse(
        Long id,
        Long walletId,
        Long userId,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String source,
        String referenceId,
        String description,
        LocalDateTime createdAt,
        Long relatedOrderId,
        String orderCode,
        PersonRef customer) {
}