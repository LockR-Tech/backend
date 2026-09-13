package com.huynqb.laundrylocker.loyalty.dto;

import jakarta.validation.constraints.NotNull;

public record RedeemPointsRequest(@NotNull Long userId, @NotNull Integer points, String reason) {
}
