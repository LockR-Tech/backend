package com.huynqb.laundrylocker.order.dto;

/// `note`: câu ghép sau nội dung mặc định của mốc, vd hạn lấy hàng ở mốc ready_for_pickup.
public record DeliveryStatusNotificationRequest(
        Long orderId,
        Long receiverUserId,
        String status,
        String eta,
        String note) {

    public DeliveryStatusNotificationRequest(Long orderId, Long receiverUserId, String status, String eta) {
        this(orderId, receiverUserId, status, eta, null);
    }
}
