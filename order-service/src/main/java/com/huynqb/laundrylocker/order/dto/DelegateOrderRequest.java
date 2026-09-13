package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;

public record DelegateOrderRequest(@NotBlank String phone, String name, String note) {
}
