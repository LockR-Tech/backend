package com.huynqb.laundrylocker.locker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/// KTV xác nhận một bước làm tay: PASSED, FAILED, hoặc NOT_APPLICABLE (bước cho phép).
public record InstallationCheckRequest(@NotBlank String status, @Size(max = 500) String note) {
}
