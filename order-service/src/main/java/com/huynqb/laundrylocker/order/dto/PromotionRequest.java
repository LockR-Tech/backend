package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromotionRequest(
        @NotBlank String code,
        @NotBlank String name,
        String discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minOrderAmount,
        Boolean stackable,
        String status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Long lockerId,
        Integer totalUsageLimit,
        Integer perUserLimit,
        @Size(max = 1000) String description) {
}
