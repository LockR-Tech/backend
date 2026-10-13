package com.huynqb.laundrylocker.common.dto;

import java.math.BigDecimal;

public record OrderSummary(Long id, Long userId, String status, BigDecimal totalPrice, String type, String orderCode) {
    public OrderSummary(Long id, Long userId, String status, BigDecimal totalPrice) {
        this(id, userId, status, totalPrice, null, null);
    }

    public OrderSummary(Long id, Long userId, String status, BigDecimal totalPrice, String type) {
        this(id, userId, status, totalPrice, type, null);
    }
}
