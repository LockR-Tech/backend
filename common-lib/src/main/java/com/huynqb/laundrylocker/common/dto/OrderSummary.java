package com.huynqb.laundrylocker.common.dto;

import java.math.BigDecimal;

/// `boxIncidentOpen`: ô của đơn (đang có hàng) đang chờ KTV xử lý sự cố — payment-service không thu tiền
/// lúc này. Null với order-service bản cũ.
public record OrderSummary(
        Long id, Long userId, String status, BigDecimal totalPrice, String type, String orderCode,
        Boolean boxIncidentOpen) {
    public OrderSummary(Long id, Long userId, String status, BigDecimal totalPrice) {
        this(id, userId, status, totalPrice, null, null, null);
    }

    public OrderSummary(Long id, Long userId, String status, BigDecimal totalPrice, String type) {
        this(id, userId, status, totalPrice, type, null, null);
    }

    public OrderSummary(Long id, Long userId, String status, BigDecimal totalPrice, String type, String orderCode) {
        this(id, userId, status, totalPrice, type, orderCode, null);
    }
}
