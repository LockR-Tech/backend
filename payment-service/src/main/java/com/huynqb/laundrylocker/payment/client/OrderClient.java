package com.huynqb.laundrylocker.payment.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.OrderSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "order-service", path = "/internal/orders")
public interface OrderClient {

    @GetMapping("/{id}")
    ApiResponse<OrderSummary> getOrder(@PathVariable Long id);

    @PostMapping("/{id}/payment-status")
    ApiResponse<Void> updatePaymentStatus(
            @PathVariable Long id,
            @RequestParam("status") String status,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "actorUserId", required = false) Long actorUserId);
}
