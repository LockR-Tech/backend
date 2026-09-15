package com.huynqb.laundrylocker.order.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.order.dto.admin.RevenueDtos.CustomerRevenue;
import com.huynqb.laundrylocker.order.dto.admin.RevenueDtos.CustomerRevenueDetailResponse;
import com.huynqb.laundrylocker.order.dto.admin.RevenueDtos.DailyRevenueResponse;
import com.huynqb.laundrylocker.order.dto.admin.RevenueDtos.LockerRevenueResponse;
import com.huynqb.laundrylocker.order.dto.admin.RevenueDtos.MethodRevenueResponse;
import com.huynqb.laundrylocker.order.dto.admin.RevenueDtos.RevenueSummaryResponse;
import com.huynqb.laundrylocker.order.dto.admin.RevenueDtos.ServiceRevenueResponse;
import com.huynqb.laundrylocker.order.dto.admin.RevenueDtos.StoreRevenueResponse;
import com.huynqb.laundrylocker.order.service.RevenueReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// Báo cáo doanh thu (tiền thực thu) cho trang /admin/revenue. from/to: yyyy-MM-dd theo giờ
/// Việt Nam, bao gồm hai đầu, tối đa 366 ngày; mặc định đầu tháng tới hôm nay.
/// `/api/admin/orders/revenue` và `/api/admin/dashboard/overview` cũ (tính theo totalPrice đơn
/// COMPLETED) giữ nguyên cho màn hình đang dùng.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/revenue")
public class AdminRevenueController {

    private final RevenueReportService revenueService;

    @GetMapping("/summary")
    public ApiResponse<RevenueSummaryResponse> summary(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(revenueService.summary(from, to));
    }

    @GetMapping("/daily")
    public ApiResponse<DailyRevenueResponse> daily(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(revenueService.daily(from, to));
    }

    @GetMapping("/by-service")
    public ApiResponse<ServiceRevenueResponse> byService(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(revenueService.byService(from, to));
    }

    @GetMapping("/by-method")
    public ApiResponse<MethodRevenueResponse> byMethod(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(revenueService.byMethod(from, to));
    }

    @GetMapping("/by-locker")
    public ApiResponse<LockerRevenueResponse> byLocker(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(revenueService.byLocker(from, to, storeId));
    }

    @GetMapping("/by-store")
    public ApiResponse<StoreRevenueResponse> byStore(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(revenueService.byStore(from, to));
    }

    @GetMapping("/by-customer")
    public ApiResponse<PageResponse<CustomerRevenue>> byCustomer(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(revenueService.byCustomer(from, to, page, size, sort, q));
    }

    @GetMapping("/customers/{userId}")
    public ApiResponse<CustomerRevenueDetailResponse> customer(
            @PathVariable Long userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ApiResponse.ok(revenueService.customerDetail(userId, from, to));
    }
}
