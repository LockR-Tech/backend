package com.huynqb.laundrylocker.payment.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.OrderSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", path = "/internal/orders")
public interface OrderClient {

    @GetMapping("/{id}")
    ApiResponse<OrderSummary> getOrder(@PathVariable Long id);
}
