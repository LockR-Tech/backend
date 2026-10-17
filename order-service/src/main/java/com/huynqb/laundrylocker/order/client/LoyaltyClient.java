package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.dto.LoyaltyRedemption;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/// Dùng / hoàn điểm thưởng cho đơn (loyalty-service sở hữu điểm). Chỉ gọi trong cụm qua Eureka;
/// gateway chặn mọi đường /internal. Cả hai lời gọi idempotent theo orderId.
@FeignClient(name = "loyalty-service", contextId = "loyaltyClient", path = "/internal/loyalty")
public interface LoyaltyClient {

    @PostMapping("/redemptions")
    ApiResponse<LoyaltyRedemption.Result> redeem(@RequestBody LoyaltyRedemption.Request request);

    @PostMapping("/redemptions/{orderId}/refund")
    ApiResponse<LoyaltyRedemption.Refund> refund(@PathVariable("orderId") Long orderId);
}
