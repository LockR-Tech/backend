package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletTransactionResponse(
        Long id,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String source,
        String referenceId,
        String description,
        LocalDateTime createdAt,
        // Chỉ có giá trị khi source = ORDER_PAYMENT. Cùng cách suy ra (WalletTransactionRefs)
        // và cùng nguồn tra cứu (order-service) với AdminWalletTransactionResponse — mã đơn
        // hiện trên app khách phải khớp với mã đơn admin thấy cho cùng một biến động.
        Long relatedOrderId,
        String orderCode) {
}
