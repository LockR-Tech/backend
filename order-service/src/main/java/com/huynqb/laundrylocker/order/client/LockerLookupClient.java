package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.dto.admin.BoxInfo;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

/// Tra cứu tủ/ô theo lô cho trang admin và báo cáo doanh thu.
@FeignClient(name = "locker-service", contextId = "lockerLookupClient")
public interface LockerLookupClient {

    /// ids null ⇒ toàn bộ tủ.
    @GetMapping("/internal/lockers/batch")
    ApiResponse<List<LockerInfo>> getLockers(@RequestParam(value = "ids", required = false) Collection<Long> ids);

    @GetMapping("/internal/boxes/batch")
    ApiResponse<List<BoxInfo>> getBoxes(@RequestParam("ids") Collection<Long> ids);
}
