package com.huynqb.laundrylocker.order.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.order.dto.OrderResponse;
import com.huynqb.laundrylocker.order.dto.UpdateOrderStatusRequest;
import com.huynqb.laundrylocker.order.dto.admin.AdminOrderResponse;
import com.huynqb.laundrylocker.order.dto.admin.OrderBriefResponse;
import com.huynqb.laundrylocker.order.service.AdminOrderQueryService;
import com.huynqb.laundrylocker.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// API admin mới cho trang /admin/orders. `GET /api/admin/orders` (danh sách phẳng) giữ
/// nguyên trong OrderController vì màn hình web khác đang dùng.
@RestController
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderQueryService queryService;
    private final OrderService orderService;

    /// status/type/paymentStatus nhận nhiều giá trị: `status=STORING,EXPIRED` hoặc lặp tham số.
    @GetMapping("/api/admin/orders/search")
    public ApiResponse<PageResponse<AdminOrderResponse>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) List<String> paymentStatus,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long lockerId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(queryService.search(new AdminOrderQueryService.SearchCriteria(
                page, size, status, type, paymentStatus, from, to, userId, lockerId, storeId, q, sort)));
    }

    @GetMapping("/api/admin/orders/{id}/detail")
    public ApiResponse<AdminOrderResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.detail(id));
    }

    /// Alias PATCH của `PUT /api/admin/orders/{id}/status` — cùng body JSON {status, staffId?, receiveBoxId?}.
    @PatchMapping("/api/admin/orders/{id}/status")
    public ApiResponse<OrderResponse> patchStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ApiResponse.ok("ORDER_STATUS_UPDATED", "Order status updated", orderService.updateStatus(id, request));
    }

    @GetMapping("/internal/orders/batch")
    public ApiResponse<List<OrderBriefResponse>> briefs(@RequestParam(required = false) List<Long> ids) {
        if (ids != null && ids.size() > 500) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "TOO_MANY_IDS", "At most 500 ids per request");
        }
        return ApiResponse.ok(queryService.briefs(ids));
    }
}
