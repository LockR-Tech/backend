package com.huynqb.laundrylocker.iot.dto;

import jakarta.validation.constraints.NotNull;

public record UnlockRequest(@NotNull Long lockerId, @NotNull Long boxId, String pinCode) {
}
