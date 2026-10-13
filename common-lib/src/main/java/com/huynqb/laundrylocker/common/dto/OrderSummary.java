package com.huynqb.laundrylocker.common.dto;

import java.math.BigDecimal;

public record OrderSummary(Long id, Long userId, String status, BigDecimal totalPrice, String type) {

    public OrderSummary(Long id, Long userId, String status, BigDecimal totalPrice) {
        this(id, userId, status, totalPrice, null);
    }
}
