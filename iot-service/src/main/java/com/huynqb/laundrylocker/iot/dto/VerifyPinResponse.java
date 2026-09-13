package com.huynqb.laundrylocker.iot.dto;

public record VerifyPinResponse(
        Boolean valid, Long orderId, Long boxId, String orderStatus, String message) {
}
