package com.huynqb.laundrylocker.iot.dto;

import jakarta.validation.constraints.NotNull;

public record PickupRequest(@NotNull Long orderId) {
}
