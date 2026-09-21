package com.huynqb.laundrylocker.iot.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.iot.dto.OrderLookupResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "order-service")
public interface OrderClient {

    @GetMapping("/api/orders/pin/{pinCode}")
    ApiResponse<OrderLookupResponse> getByPin(@PathVariable String pinCode);

    @GetMapping("/internal/orders/by-access")
    ApiResponse<OrderLookupResponse> getByAccess(@RequestParam("code") String code);

    // Các lệnh dưới gọi thẳng order-service (không qua gateway) thay mặt chủ đơn, sau
    // khi iot-service đã xác thực mã và thấy ô của đơn vừa được mở thật.

    @PutMapping("/api/orders/{orderId}/complete")
    ApiResponse<OrderLookupResponse> complete(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId);

    @PutMapping("/api/orders/{orderId}/confirm")
    ApiResponse<OrderLookupResponse> confirm(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId);

    @PostMapping("/api/orders/{orderId}/pickup-storage")
    ApiResponse<OrderLookupResponse> pickupStorage(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId);

    @PostMapping("/internal/orders/{orderId}/drop-opened")
    ApiResponse<Void> dropOpened(@PathVariable Long orderId);
}
