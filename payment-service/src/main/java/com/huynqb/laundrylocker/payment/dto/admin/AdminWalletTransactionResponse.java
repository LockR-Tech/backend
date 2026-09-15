package com.huynqb.laundrylocker.payment.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Biến động ví cho trang admin: mọi field WalletTransactionResponse + walletId, userId, khách.
/// relatedOrderId: id đơn khi source = ORDER_PAYMENT (referenceId dạng ORDERPAY-{orderId}).
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
        PersonRef customer) {
}