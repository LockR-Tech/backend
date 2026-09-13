package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;

public record OrderDetailResponse(Long serviceId, BigDecimal quantity, BigDecimal price, String description) {
}
