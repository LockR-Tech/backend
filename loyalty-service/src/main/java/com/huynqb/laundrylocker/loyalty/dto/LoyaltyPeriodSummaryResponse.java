package com.huynqb.laundrylocker.loyalty.dto;

import java.time.LocalDate;

/// Điểm cộng/trừ trong một khoảng ngày so với khoảng liền trước cùng độ dài; % null khi kỳ trước bằng 0.
public record LoyaltyPeriodSummaryResponse(
        LocalDate from,
        LocalDate to,
        LocalDate previousFrom,
        LocalDate previousTo,
        Totals current,
        Totals previous,
        Double issuedPct,
        Double redeemedPct,
        Double netPct,
        Double transactionsPct) {

    public record Totals(long issued, long redeemed, long net, long transactions) {
    }
}
