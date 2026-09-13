package com.huynqb.laundrylocker.common.dto;

import java.math.BigDecimal;

public record OrderSummary(Long id, Long userId, String status, BigDecimal totalPrice) {
}
