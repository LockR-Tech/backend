package com.huynqb.laundrylocker.loyalty.dto;

import jakarta.validation.constraints.NotNull;

public record AdjustPointsRequest(@NotNull Long userId, Long orderId, @NotNull Integer points, String type) {
}
