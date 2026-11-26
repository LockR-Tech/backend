package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.dto.admin.BoxInfo;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/// Tra cứu tủ/ô theo lô cho trang admin và báo cáo doanh thu.
@FeignClient(name = "locker-service", contextId = "lockerLookupClient")
public interface LockerLookupClient {

    /// ids null ⇒ toàn bộ tủ.
    @GetMapping("/internal/lockers/batch")
    ApiResponse<List<LockerInfo>> getLockers(@RequestParam(value = "ids", required = false) Collection<Long> ids);

    @GetMapping("/internal/boxes/batch")
    ApiResponse<List<BoxInfo>> getBoxes(@RequestParam("ids") Collection<Long> ids);

    /// `{lockerId, visible}` — tủ demo đã ẩn (cụm "Giả lập & demo", F3-G14) ⇒ visible=false với khách thường.
    @GetMapping("/internal/lockers/{id}/customer-visible")
    ApiResponse<Map<String, Object>> customerVisible(
            @PathVariable("id") Long id, @RequestParam(value = "userId", required = false) Long userId);
}
