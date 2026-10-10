package com.huynqb.laundrylocker.store.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/// Tra cứu tủ thuộc một cửa hàng ở locker-service (qua Eureka), dùng trước khi xoá cửa hàng.
@FeignClient(name = "locker-service", contextId = "storeLockerClient")
public interface LockerClient {

    /// Mọi tủ của cửa hàng, không lọc trạng thái.
    @GetMapping("/api/lockers")
    ApiResponse<List<LockerRef>> lockersByStore(@RequestParam("storeId") Long storeId);

    /// Phần cần dùng của LockerResponse bên locker-service; field khác bị bỏ qua.
    record LockerRef(Long id, Long storeId, String code, String name) {
    }
}
