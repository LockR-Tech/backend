package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderItemRequest(@NotNull Long serviceId, BigDecimal quantity, String description) {
}
