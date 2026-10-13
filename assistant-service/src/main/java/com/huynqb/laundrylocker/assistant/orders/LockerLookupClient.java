package com.huynqb.laundrylocker.assistant.orders;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

/// Tra tên/địa chỉ tủ và số ô theo lô cho đơn của người hỏi (`OrderResponse` chỉ có id).
@FeignClient(name = "locker-service", contextId = "assistantLockerClient")
public interface LockerLookupClient {

    @GetMapping("/internal/lockers/batch")
    ApiResponse<List<OrderDtos.LockerInfo>> lockers(@RequestParam("ids") Collection<Long> ids);

    @GetMapping("/internal/boxes/batch")
    ApiResponse<List<OrderDtos.BoxInfo>> boxes(@RequestParam("ids") Collection<Long> ids);
}
