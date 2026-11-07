package com.huynqb.laundrylocker.iot.dto;

/// order-service `/internal/orders/{id}/ble-access` (OrderBleAccessResponse): được mở ô của đơn qua Bluetooth
/// không. `action` khác null ⇒ được (DROP | RENTAL_ACCESS | PICKUP); không thì `denyCode`/`denyMessage`/`denyStatus`.
public record OrderBleAccessView(
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
}
