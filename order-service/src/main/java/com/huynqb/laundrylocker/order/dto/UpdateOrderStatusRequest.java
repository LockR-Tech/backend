package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateOrderStatusRequest(@NotBlank String status, Long staffId, Long receiveBoxId) {
}
