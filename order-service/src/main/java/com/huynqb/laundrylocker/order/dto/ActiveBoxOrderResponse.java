package com.huynqb.laundrylocker.order.dto;

public record ActiveBoxOrderResponse(
        Long orderId,
        String orderCode,
        Long userId,
        String receiverName,
        String receiverPhone,
        String type,
        String status,
        String pinCode,
        Long boxId
) {}
