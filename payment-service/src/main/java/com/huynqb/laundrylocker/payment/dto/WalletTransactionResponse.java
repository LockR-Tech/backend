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
        LocalDateTime createdAt) {
}
