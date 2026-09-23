package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;

public record WithdrawableBalanceResponse(
        Long userId,
        BigDecimal totalBalance,
        BigDecimal withdrawableBalance,
        String currency
) {}
