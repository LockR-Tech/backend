package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LockerReportRequest(@NotNull Long userId, @NotBlank String title, @NotBlank String description) {
}
