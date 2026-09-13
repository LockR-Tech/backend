package com.huynqb.laundrylocker.loyalty.dto;

import jakarta.validation.constraints.NotNull;

public record RedeemStampRequest(@NotNull Long userId, @NotNull Integer stamps, String rewardName) {
}
