package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record TransactionMethodTotalResponse(
        String period,
        String selectedMethod,
        BigDecimal totalAmount,
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        int transactionCount,
        Map<String, BigDecimal> summary,
        List<MethodTotalItem> methods
) {
    public record MethodTotalItem(
            String method,
            String label,
            BigDecimal totalAmount,
            BigDecimal totalExpense,
            BigDecimal totalIncome,
            int transactionCount
    ) {}
}
