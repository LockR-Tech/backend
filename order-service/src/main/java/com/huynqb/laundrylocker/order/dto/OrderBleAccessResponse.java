package com.huynqb.laundrylocker.order.dto;

/// iot-service hỏi trước khi cấp mã mở ô qua Bluetooth cho một đơn (F3-G11): tài khoản này có được mở ô
/// của đơn lúc này không, và mở để làm gì.
///
/// Luôn trả 200: được mở ⇒ `action` (DROP | RENTAL_ACCESS | PICKUP) + tủ/ô; không được ⇒ `denyCode`
/// (ORDER_NOT_FOUND, BLE_ORDER_FORBIDDEN, BLE_ORDER_NOT_OPENABLE, ORDER_UNPAID, RENTAL_EXPIRED…),
/// `denyMessage` và `denyStatus` (mã HTTP iot-service trả cho app).
public record OrderBleAccessResponse(
        Long orderId,
        Long userId,
        Long lockerId,
        Long boxId,
        String status,
        String type,
        String action,
        String denyCode,
        String denyMessage,
        Integer denyStatus) {

    public static OrderBleAccessResponse denied(Long orderId, String code, String message, int httpStatus) {
        return new OrderBleAccessResponse(orderId, null, null, null, null, null, null, code, message, httpStatus);
    }
}
