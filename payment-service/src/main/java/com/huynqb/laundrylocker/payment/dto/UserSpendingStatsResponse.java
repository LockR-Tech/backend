package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;
import java.util.Map;

public record UserSpendingStatsResponse(
        String period,
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        BigDecimal netChange,
        int transactionCount,
        Map<String, BigDecimal> byService,
        Map<String, BigDecimal> byMethod
) {
}
