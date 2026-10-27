package com.huynqb.laundrylocker.order.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.order.dto.DroneFlightControlViews;
import com.huynqb.laundrylocker.order.dto.DroneWeatherViews;
import com.huynqb.laundrylocker.order.model.DroneFlightBlock;
import com.huynqb.laundrylocker.order.model.DroneStationSuspensionLog;
import com.huynqb.laundrylocker.order.service.DroneFlightControl;
import com.huynqb.laundrylocker.order.service.DroneWeatherQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/// Trang "Thời tiết & bay" của admin (ADMIN — gateway chặn /api/admin/**): thời tiết và lệnh dừng từng trạm,
/// dừng / mở lại trạm, lịch sử chặn bay. Tắt bay toàn hệ thống và ngưỡng thời tiết là quy tắc nghiệp vụ
/// (`/api/admin/settings/order`).
@RestController
@RequestMapping("/api/admin/drone-flight-control")
@RequiredArgsConstructor
public class DroneFlightControlController {

    private final DroneFlightControl flightControl;
    private final DroneWeatherQueryService weatherQueryService;

    @GetMapping("/stations")
    public ApiResponse<List<DroneWeatherViews.LockerWeather>> stations() {
        return ApiResponse.ok(weatherQueryService.droneLockers());
    }

    @PutMapping("/stations/{lockerId}/suspension")
    public ApiResponse<DroneFlightControlViews.StationSuspension> suspend(
            @PathVariable Long lockerId,
            @RequestBody(required = false) DroneFlightControlViews.SuspendStationRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("DRONE_STATION_SUSPENDED", "Đã tạm dừng bay ở trạm",
                flightControl.suspend(lockerId, request, userId));
    }

    @DeleteMapping("/stations/{lockerId}/suspension")
    public ApiResponse<Void> resume(@PathVariable Long lockerId, @RequestHeader("X-User-Id") Long userId) {
        flightControl.resume(lockerId, userId);
        return ApiResponse.ok("DRONE_STATION_RESUMED", "Đã mở lại bay ở trạm");
    }

    @GetMapping("/suspension-logs")
    public ApiResponse<List<DroneStationSuspensionLog>> suspensionLogs(
            @RequestParam(required = false) Long lockerId,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(flightControl.suspensionLogs(lockerId, limit));
    }

    /// `from`/`to` là ngày (yyyy-MM-dd), `to` tính trọn ngày.
    @GetMapping("/blocks")
    public ApiResponse<PageResponse<DroneFlightBlock>> blocks(
            @RequestParam(required = false) String cause,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long lockerId,
            @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DroneFlightBlock> result = flightControl.searchBlocks(
                cause, action, lockerId, orderCode,
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return ApiResponse.ok(new PageResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/blocks/summary")
    public ApiResponse<Map<String, Long>> blockSummary(@RequestParam(defaultValue = "24") int hours) {
        return ApiResponse.ok(flightControl.blockCounts(hours));
    }
}
