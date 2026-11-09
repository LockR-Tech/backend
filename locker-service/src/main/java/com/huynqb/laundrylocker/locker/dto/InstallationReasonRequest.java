package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/// Lý do admin trả hồ sơ lắp đặt về KTV hoặc huỷ hồ sơ.
public record InstallationReasonRequest(@NotBlank @Size(max = 500) String reason) {
}
