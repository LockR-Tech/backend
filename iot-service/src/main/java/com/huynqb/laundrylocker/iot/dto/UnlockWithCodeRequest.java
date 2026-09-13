package com.huynqb.laundrylocker.iot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/// Kiosk tủ mở khóa chỉ bằng MÃ (PIN 6 số, QR token LLQR..., hoặc mã ủy
/// quyền) mà không cần biết trước boxId — iot-service tự tra đơn theo mã và
/// suy ra ô tương ứng.
public record UnlockWithCodeRequest(@NotNull Long lockerId, @NotBlank String code) {
}
