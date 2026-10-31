package com.huynqb.laundrylocker.loyalty.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "order-service", path = "/internal/orders")
public interface OrderClient {

    /// Tối đa 500 id mỗi lần gọi.
    @GetMapping("/batch")
    ApiResponse<List<OrderBrief>> briefs(@RequestParam("ids") List<Long> ids);

    record OrderBrief(Long id, String orderCode, String type) {
    }
}
