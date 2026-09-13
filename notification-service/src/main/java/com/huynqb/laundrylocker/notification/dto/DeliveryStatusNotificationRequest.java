package com.huynqb.laundrylocker.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Yêu cầu gửi noti trạng thái giao hàng (drone) cho người nhận.
 *
 * <p>Được gọi nội bộ (service-to-service) bởi luồng giao drone/order, KHÔNG expose
 * ra ngoài qua gateway. Backend gửi tới mọi thiết bị của {@code receiverUserId}.
 *
 * @param orderId        đơn hàng để mobile deep-link sang chi tiết đơn
 * @param receiverUserId user nhận hàng (đích gửi noti)
 * @param status         một trong: dispatched/approaching/arrived/delivered/delayed/failed
 * @param eta            thời gian dự kiến (tuỳ chọn, chỉ dùng cho mốc delayed)
 */
public record DeliveryStatusNotificationRequest(
        @NotNull Long orderId,
        @NotNull Long receiverUserId,
        @NotBlank String status,
        String eta) {
}
