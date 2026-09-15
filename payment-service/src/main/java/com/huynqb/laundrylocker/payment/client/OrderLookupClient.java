package com.huynqb.laundrylocker.payment.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.payment.dto.admin.OrderBrief;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

/// Tra cứu đơn theo lô cho trang admin thanh toán (tách contextId khỏi OrderClient).
@FeignClient(name = "order-service", contextId = "orderLookupClient", path = "/internal/orders")
public interface OrderLookupClient {

    @GetMapping("/batch")
    ApiResponse<List<OrderBrief>> getOrders(@RequestParam("ids") Collection<Long> ids);
}