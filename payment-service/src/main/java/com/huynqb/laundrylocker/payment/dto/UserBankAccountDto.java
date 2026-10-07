package com.huynqb.laundrylocker.payment.dto;

import java.time.LocalDateTime;

public record UserBankAccountDto(
        Long userId,
        String bankName,
        String bankCode,
        String accountNumber,
        String accountHolderName,
        LocalDateTime updatedAt
) {}
