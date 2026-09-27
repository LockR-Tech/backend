package com.huynqb.laundrylocker.locker.dto;

public record ActiveBoxOrderDto(
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
