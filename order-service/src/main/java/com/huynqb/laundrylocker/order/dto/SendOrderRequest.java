package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SendOrderRequest(
        @NotNull Long lockerId,
        Long boxId,
        String size,
        @NotBlank String receiverPhone,
        String receiverName,
        String note,
        BigDecimal totalPrice,
        String promotionCode) {
}
