package com.huynqb.laundrylocker.iot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VerifyPinRequest(@NotNull Long boxId, @NotBlank String pinCode) {
}
