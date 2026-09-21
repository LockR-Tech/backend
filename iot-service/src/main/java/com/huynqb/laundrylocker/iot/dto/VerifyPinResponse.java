package com.huynqb.laundrylocker.iot.dto;

public record VerifyPinResponse(
        Boolean valid,
        Long orderId,
        Long boxId,
        String orderStatus,
        String message,
        Long orderUserId,
        String orderType,
        // Lý do từ chối có mã máy đọc được (VD ORDER_UNPAID, RENTAL_EXPIRED) để kiosk/app
        // hướng dẫn tiếp; null khi mã hợp lệ hoặc lỗi không phân loại.
        String reasonCode) {

    public VerifyPinResponse(
            Boolean valid, Long orderId, Long boxId, String orderStatus, String message, Long orderUserId) {
        this(valid, orderId, boxId, orderStatus, message, orderUserId, null, null);
    }
}
