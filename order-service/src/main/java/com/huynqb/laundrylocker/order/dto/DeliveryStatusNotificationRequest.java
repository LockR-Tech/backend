package com.huynqb.laundrylocker.order.dto;

public record DeliveryStatusNotificationRequest(
        Long orderId,
        Long receiverUserId,
        String status,
        String eta) {
}
