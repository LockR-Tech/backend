package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WithdrawResponse(
        Long id,
        String referenceId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        BigDecimal withdrawableBalance,
        String bankName,
        String bankCode,
        String accountNumber,
        String accountHolderName,
        String status,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {}
