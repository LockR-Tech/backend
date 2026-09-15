package com.huynqb.laundrylocker.payment.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.payment.dto.internal.CollectedPaymentsResponse;
import com.huynqb.laundrylocker.payment.dto.internal.OrderPaymentSummary;
import com.huynqb.laundrylocker.payment.service.InternalPaymentReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// API nội bộ cho order-service (gateway chặn /internal từ ngoài).
@RestController
@RequiredArgsConstructor
public class InternalPaymentReportController {

    private final InternalPaymentReportService reportService;

    @GetMapping("/internal/payments/order-summaries")
    public ApiResponse<List<OrderPaymentSummary>> orderSummaries(@RequestParam(required = false) List<Long> orderIds) {
        return ApiResponse.ok(reportService.orderSummaries(orderIds));
    }

    /// from/to: ISO local date-time theo múi giờ JVM (UTC trên container), nửa mở [from, to).
    @GetMapping("/internal/payments/collected")
    public ApiResponse<CollectedPaymentsResponse> collected(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Long userId) {
        return ApiResponse.ok(reportService.collected(from, to, userId));
    }
}
