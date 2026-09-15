package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/// `hours` giới hạn theo cấu hình admin (app.order.rental-min-hours / rental-max-hours), kiểm trong OrderService.
public record RentalOrderRequest(
        @NotNull Long lockerId,
        Long boxId,
        String cellType,
        @NotNull @Min(1) Integer hours,
        String note,
        String promotionCode) {
}
