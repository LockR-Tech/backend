package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.dto.OrderRefundResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/// Hoàn tiền một đơn đã huỷ về ví của người trả (payment-service sở hữu tiền).
@FeignClient(name = "payment-service", contextId = "paymentRefundClient", path = "/internal/payments")
public interface PaymentRefundClient {

    @PostMapping("/orders/{orderId}/refund")
    ApiResponse<OrderRefundResult> refundOrder(
            @PathVariable Long orderId,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "actorUserId", required = false) Long actorUserId);
}
