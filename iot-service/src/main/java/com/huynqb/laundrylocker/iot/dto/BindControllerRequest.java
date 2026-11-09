package com.huynqb.laundrylocker.iot.dto;

import jakarta.validation.constraints.NotBlank;

/// KTV lắp đặt gán Pi vào tủ: `macAddress` là MAC gõ tay hoặc nội dung QR kiosk hiện
/// (`LOCKR-PI:AA:BB:CC:DD:EE:FF`).
public record BindControllerRequest(@NotBlank String macAddress) {
}
