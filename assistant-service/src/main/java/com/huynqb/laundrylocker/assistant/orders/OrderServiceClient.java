package com.huynqb.laundrylocker.assistant.orders;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/// Gọi thẳng order-service trong mạng nội bộ (không qua gateway) thay mặt người hỏi — cùng cách
/// iot-service đang làm. `userId` luôn lấy từ header X-User-Id gateway đã gắn sau khi kiểm JWT, không
/// bao giờ từ nội dung câu hỏi. Timeout ngắn: xem `spring.cloud.openfeign.client.config` trong
/// application.yml.
@FeignClient(name = "order-service", contextId = "assistantOrderClient")
public interface OrderServiceClient {

    /// Đơn người dùng đã tạo, mới nhất trước.
    @GetMapping("/api/orders/my-orders")
    ApiResponse<List<OrderDtos.MyOrder>> myOrders(@RequestHeader("X-User-Id") Long userId);

    /// Đơn người khác gửi cho người dùng, mới nhất trước.
    @GetMapping("/api/orders/incoming")
    ApiResponse<List<OrderDtos.IncomingOrder>> incoming(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("activeOnly") boolean activeOnly,
            @RequestParam("limit") int limit);
}
