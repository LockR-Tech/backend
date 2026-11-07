package com.huynqb.laundrylocker.iot.dto;

import java.time.Instant;

/// Mã mở một ô qua Bluetooth: app ghi `token` (base64url → 91 byte) vào characteristic `openCharUuid`
/// của thiết bị BLE tên `deviceName`, rồi chờ Pi báo kết quả có cùng `nonce` trên `resultCharUuid`.
/// Mã thử của admin và mã theo đơn dùng chung dạng này (mã 91 byte v1 như nhau).
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
        String resultCharUuid,
        /// Mã theo đơn (`/api/iot/ble/orders/{orderId}/token`): đơn và việc mở ô để làm gì —
        /// DROP | RENTAL_ACCESS | PICKUP. Null với mã thử của admin.
        Long orderId,
        String action) {

    /// Mã thử của admin — không gắn với đơn.
    public BleOpenTokenResponse(String token, String nonce, Instant expiresAt, Long lockerId, String lockerCode,
                                Integer boxNumber, Long boxId, String boxStatus, String deviceName, String serviceUuid,
                                String openCharUuid, String resultCharUuid) {
        this(token, nonce, expiresAt, lockerId, lockerCode, boxNumber, boxId, boxStatus, deviceName, serviceUuid,
                openCharUuid, resultCharUuid, null, null);
    }
}
