package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.dto.DronePositionUpdate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/// Đẩy vị trí drone lên STOMP `/topic/deliveries/{orderId}/position` qua
/// notification-service (tách contextId vì đường dẫn không nằm dưới /internal/notifications).
@FeignClient(name = "notification-service", contextId = "dronePositionClient")
public interface DronePositionClient {

    @PostMapping("/internal/deliveries/{orderId}/position")
    ApiResponse<Map<String, Object>> publish(@PathVariable Long orderId, @RequestBody DronePositionUpdate position);
}
