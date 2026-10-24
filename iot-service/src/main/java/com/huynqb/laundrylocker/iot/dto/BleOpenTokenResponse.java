package com.huynqb.laundrylocker.iot.dto;

import java.time.Instant;

/// Mã mở một ô qua Bluetooth: app ghi `token` (base64url → 91 byte) vào characteristic `openCharUuid`
/// của thiết bị BLE tên `deviceName`, rồi chờ Pi báo kết quả có cùng `nonce` trên `resultCharUuid`.
public record BleOpenTokenResponse(
        String token,
        String nonce,
        Instant expiresAt,
        Long lockerId,
        String lockerCode,
        Integer boxNumber,
        Long boxId,
        String boxStatus,
        String deviceName,
        String serviceUuid,
        String openCharUuid,
        String resultCharUuid) {
}
