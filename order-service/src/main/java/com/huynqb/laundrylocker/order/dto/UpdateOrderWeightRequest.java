package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record UpdateOrderWeightRequest(
        @NotNull BigDecimal actualWeight, String weightUnit, List<OrderItemRequest> items, String staffNote) {
}
