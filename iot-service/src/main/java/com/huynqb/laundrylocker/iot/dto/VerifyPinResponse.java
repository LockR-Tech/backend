package com.huynqb.laundrylocker.iot.dto;

public record VerifyPinResponse(
        Boolean valid,
        Long orderId,
        Long boxId,
        String orderStatus,
        String message,
        Long orderUserId,
        String orderType) {

    public VerifyPinResponse(
            Boolean valid, Long orderId, Long boxId, String orderStatus, String message, Long orderUserId) {
        this(valid, orderId, boxId, orderStatus, message, orderUserId, null);
    }
}
