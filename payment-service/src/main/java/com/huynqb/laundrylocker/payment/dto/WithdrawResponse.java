package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// `userName`/`userPhone` chỉ được ghép cho danh sách admin (tra user-service theo lô);
/// null ở API của khách hoặc khi user-service không tra được.
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
        LocalDateTime processedAt,
        Long userId,
        String userName,
        String userPhone
) {}
