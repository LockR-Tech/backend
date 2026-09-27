package com.huynqb.laundrylocker.locker.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.locker.dto.ActiveBoxOrderDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "order-service", path = "/internal/orders")
public interface OrderClient {

    @GetMapping("/active-by-box/{boxId}")
    ApiResponse<ActiveBoxOrderDto> getActiveOrderByBox(@PathVariable("boxId") Long boxId);

    @PostMapping("/{id}/relocate-box")
    ApiResponse<Map<String, Object>> relocateBox(
            @PathVariable("id") Long id,
            @RequestParam("newBoxId") Long newBoxId,
            @RequestParam(value = "newBoxNumber", required = false) Integer newBoxNumber);

    @PostMapping("/{id}/direct-handover")
    ApiResponse<Map<String, Object>> directHandover(
            @PathVariable("id") Long id,
            @RequestParam(value = "otp", required = false) String otp);

    @PostMapping("/{id}/hub-escrow")
    ApiResponse<Map<String, Object>> hubEscrow(
            @PathVariable("id") Long id,
            @RequestParam("sealNumber") String sealNumber);
}
