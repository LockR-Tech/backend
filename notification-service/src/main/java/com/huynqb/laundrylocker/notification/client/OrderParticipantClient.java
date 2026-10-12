package com.huynqb.laundrylocker.notification.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.notification.dto.OrderParticipants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/// Hỏi order-service ai được theo dõi một đơn (chủ đơn, tài khoản người nhận).
@FeignClient(name = "order-service", contextId = "orderParticipantClient", path = "/internal/orders")
public interface OrderParticipantClient {

    @GetMapping("/{orderId}/participants")
    ApiResponse<OrderParticipants> participants(@PathVariable("orderId") Long orderId);
}
