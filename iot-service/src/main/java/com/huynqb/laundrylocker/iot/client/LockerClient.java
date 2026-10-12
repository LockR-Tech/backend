package com.huynqb.laundrylocker.iot.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.iot.dto.LockerLayoutView;
import com.huynqb.laundrylocker.iot.dto.LockerView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "locker-service")
public interface LockerClient {

    @PostMapping("/api/boxes/{id}/open")
    ApiResponse<LockerBoxSummary> openBox(@PathVariable Long id);

    /// Toàn bộ ô của tủ kèm `boxNumber` — nguồn của `slotIndex` trong lệnh MQTT (ADR-0008).
    @GetMapping("/internal/lockers/{id}/layout")
    ApiResponse<LockerLayoutView> layout(@PathVariable Long id);

    /// Không truyền `ids` ⇒ toàn bộ tủ (kèm KTV phụ trách).
    @GetMapping("/internal/lockers/batch")
    ApiResponse<List<LockerView>> lockers(@RequestParam(value = "ids", required = false) List<Long> ids);

    /// Tủ báo mở ô thất bại (FAILED/JAMMED) ⇒ ô chuyển FAULT + mở/gộp phiếu sự cố (docs F3.07).
    /// Body giống `BoxFaultRequest` của locker-service (`reason`).
    @PostMapping("/internal/boxes/{id}/fault")
    ApiResponse<Map<String, Object>> markFault(@PathVariable Long id, @RequestBody Map<String, Object> body);
}
