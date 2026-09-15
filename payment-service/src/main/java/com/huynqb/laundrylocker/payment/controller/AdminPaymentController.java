package com.huynqb.laundrylocker.payment.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.payment.dto.admin.AdminPaymentDetailResponse;
import com.huynqb.laundrylocker.payment.dto.admin.AdminPaymentResponse;
import com.huynqb.laundrylocker.payment.dto.admin.AdminRefundResponse;
import com.huynqb.laundrylocker.payment.dto.admin.AdminWalletTransactionResponse;
import com.huynqb.laundrylocker.payment.service.AdminPaymentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// API admin mới cho trang /admin/payments (gateway đã route /api/admin/payments/** và
/// yêu cầu ADMIN). `GET /api/admin/payments` (danh sách phẳng) giữ nguyên.
@RestController
@RequiredArgsConstructor
public class AdminPaymentController {

    private final AdminPaymentQueryService queryService;
    private final com.huynqb.laundrylocker.payment.service.PaymentStatsService statsService;

    /// from/to: yyyy-MM-dd theo giờ Việt Nam, mặc định đầu tháng tới hôm nay.
    @GetMapping("/api/admin/payments/stats")
    public ApiResponse<com.huynqb.laundrylocker.payment.dto.admin.PaymentStatsResponse> stats(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ApiResponse.ok(statsService.stats(from, to));
    }

    @GetMapping("/api/admin/payments/search")
    public ApiResponse<PageResponse<AdminPaymentResponse>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> method,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Boolean includeTopups,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(queryService.searchPayments(new AdminPaymentQueryService.PaymentCriteria(
                page, size, status, method, kind, includeTopups, from, to, userId, orderId, q, sort)));
    }

    @GetMapping("/api/admin/payments/{id}/detail")
    public ApiResponse<AdminPaymentDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.paymentDetail(id));
    }

    @GetMapping("/api/admin/payments/refunds")
    public ApiResponse<PageResponse<AdminRefundResponse>> refunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long paymentId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(queryService.searchRefunds(new AdminPaymentQueryService.RefundCriteria(
                page, size, status, from, to, orderId, paymentId, userId, sort)));
    }

    @GetMapping("/api/admin/payments/wallet-transactions")
    public ApiResponse<PageResponse<AdminWalletTransactionResponse>> walletTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false) List<String> source,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(queryService.searchWalletTransactions(new AdminPaymentQueryService.WalletCriteria(
                page, size, userId, type, source, from, to, q, sort)));
    }
}
