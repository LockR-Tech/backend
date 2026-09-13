package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RentalOrderRequest(
        @NotNull Long lockerId,
        Long boxId,
        String cellType,
        @NotNull @Min(1) @Max(720) Integer hours,
        String note,
        String promotionCode) {
}
