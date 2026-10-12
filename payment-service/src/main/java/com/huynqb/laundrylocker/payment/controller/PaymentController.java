package com.huynqb.laundrylocker.payment.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.payment.dto.*;
import com.huynqb.laundrylocker.payment.service.PaymentService;
import com.huynqb.laundrylocker.payment.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final WalletService walletService;

    @GetMapping("/api/payments/total-by-method")
    public ApiResponse<com.huynqb.laundrylocker.payment.dto.TransactionMethodTotalResponse> totalByMethod(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false, defaultValue = "ALL") String method,
            @RequestParam(required = false, defaultValue = "ALL") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ApiResponse.ok(
                "TOTAL_BY_METHOD_OK",
                "Tổng tiền theo hình thức giao dịch",
                walletService.calculateTotalByMethod(userId, method, period, from, to));
    }

    @PostMapping("/api/payments/topup/create")
    public ApiResponse<TopupResponse> createTopup(
            @Valid @RequestBody CreateTopupRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("TOPUP_URL_CREATED", "Topup URL created", paymentService.createTopupUrl(userId, request));
    }

    @PostMapping("/api/payments/checkout")
    public ApiResponse<PaymentResponse> checkout(
            @Valid @RequestBody CheckoutRequest request, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("PAYMENT_CHECKOUT", "Checkout processed", paymentService.checkout(userId, request));
    }

    /// Tạo giao dịch với userId/số tiền tuỳ ý trong body — chỉ ADMIN (đường cũ ngoài /api/admin). Khách thanh
    /// toán đơn qua /api/payments/checkout (số tiền lấy từ đơn).
    @PostMapping("/api/payments")
    public ApiResponse<PaymentResponse> create(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        requireAdmin(roles, "PAYMENT_CREATE_FORBIDDEN", "Chỉ quản trị viên được tạo giao dịch trực tiếp");
        return ApiResponse.ok("PAYMENT_CREATED", "Payment created", paymentService.create(request));
    }

    @PostMapping("/api/payments/create")
    public ApiResponse<PaymentResponse> createLegacy(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return create(request, roles);
    }

    private static void requireAdmin(String roles, String code, String message) {
        if (!com.huynqb.laundrylocker.common.security.UserRoles.isAdmin(roles)) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    code, message, org.springframework.http.HttpStatus.FORBIDDEN);
        }
    }

    /// Đường cũ ngoài /api/admin (gateway không chặn theo vai trò) — chỉ ADMIN được đổi trạng thái giao dịch.
    @PatchMapping("/api/payments/{id}/status")
    public ApiResponse<PaymentResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePaymentStatusRequest request,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        if (!com.huynqb.laundrylocker.common.security.UserRoles.isAdmin(roles)) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "PAYMENT_STATUS_FORBIDDEN", "Chỉ quản trị viên được đổi trạng thái giao dịch",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        return adminUpdateStatus(id, request);
    }

    private ApiResponse<PaymentResponse> adminUpdateStatus(Long id, UpdatePaymentStatusRequest request) {
        return ApiResponse.ok("PAYMENT_STATUS_UPDATED", "Payment status updated", paymentService.updateStatus(id, request));
    }

    /// Người trả, chủ đơn hoặc ADMIN (khác ⇒ 403 ORDER_FORBIDDEN).
    @GetMapping("/api/payments/{id}")
    public ApiResponse<PaymentResponse> get(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(paymentService.get(id, userId, roles));
    }

    /// Chủ đơn hoặc ADMIN (khác ⇒ 403 ORDER_FORBIDDEN).
    @GetMapping("/api/payments")
    public ApiResponse<List<PaymentResponse>> listByOrder(
            @RequestParam Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(paymentService.listByOrder(orderId, userId, roles));
    }

    @GetMapping("/api/payments/order/{orderId}")
    public ApiResponse<List<PaymentResponse>> listByOrderLegacy(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return listByOrder(orderId, userId, roles);
    }

    @GetMapping(value = "/api/payments/sepay/pay", produces = org.springframework.http.MediaType.TEXT_HTML_VALUE)
    public String sepayPayHtml(@RequestParam String referenceId) {
        return paymentService.getSepayPayHtml(referenceId);
    }

    @PostMapping("/api/payments/sepay/webhook")
    public Map<String, Object> sepayWebhook(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        paymentService.handleSepayWebhook(body, authHeader);
        return Map.of("success", true, "message", "SePay webhook processed");
    }

    /// Trang quay về sau cổng SePay: chỉ báo trạng thái hiện tại, không ghi nhận tiền (webhook mới ghi nhận).
    @GetMapping({"/api/payments/sepay/return", "/payments/sepay/callback"})
    public ApiResponse<SepayReturnStatusResponse> sepayCallback(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(
                "SEPAY_RETURN_STATUS", "Trạng thái giao dịch SePay", paymentService.sepayReturnStatus(params));
    }

    /// Tạo yêu cầu hoàn tiền cho một giao dịch bất kỳ — chỉ ADMIN. Khách dùng /api/payments/refund-request.
    @PostMapping("/api/payments/{paymentId}/refund")
    public ApiResponse<RefundResponse> refund(
            @PathVariable Long paymentId,
            @Valid @RequestBody RefundRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        requireAdmin(roles, "REFUND_CREATE_FORBIDDEN", "Chỉ quản trị viên được tạo hoàn tiền cho giao dịch");
        return ApiResponse.ok("REFUND_CREATED", "Refund created", paymentService.refund(paymentId, request, userId));
    }

    @GetMapping("/api/payments/bank-account")
    public ApiResponse<UserBankAccountDto> getBankAccount(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("BANK_ACCOUNT_OK", "Bank account retrieved", paymentService.getUserBankAccount(userId));
    }

    @PutMapping("/api/payments/bank-account")
    public ApiResponse<UserBankAccountDto> saveBankAccount(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody SaveBankAccountRequest request) {
        return ApiResponse.ok("BANK_ACCOUNT_SAVED", "Bank account saved", paymentService.saveUserBankAccount(userId, request));
    }

    @PostMapping("/api/payments/refund-request")
    public ApiResponse<RefundResponse> requestRefund(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateRefundRequest request) {
        return ApiResponse.ok("REFUND_REQUESTED", "Refund requested", paymentService.requestRefund(userId, request));
    }

    /// Chủ đơn hoặc ADMIN (khác ⇒ 403 ORDER_FORBIDDEN).
    @GetMapping("/api/payments/order/{orderId}/refunds")
    public ApiResponse<List<RefundResponse>> refunds(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(paymentService.refundsByOrder(orderId, userId, roles));
    }

    @GetMapping("/api/payments/my-refunds")
    public ApiResponse<List<RefundResponse>> myRefunds(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(paymentService.myRefunds(userId));
    }

    /// Người trả, chủ đơn hoặc ADMIN (khác ⇒ 403 ORDER_FORBIDDEN).
    @GetMapping("/api/payments/refund/{refundId}")
    public ApiResponse<RefundResponse> refundStatus(
            @PathVariable Long refundId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return ApiResponse.ok(paymentService.getRefund(refundId, userId, roles));
    }

    @GetMapping("/api/admin/payments")
    public ApiResponse<List<PaymentResponse>> adminList() {
        return ApiResponse.ok(paymentService.listAll());
    }

    @PatchMapping("/api/admin/payments/{id}/status")
    public ApiResponse<PaymentResponse> adminStatus(
            @PathVariable Long id, @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return adminUpdateStatus(id, request);
    }

    @GetMapping("/api/admin/payments/{paymentId}")
    public ApiResponse<PaymentResponse> adminGet(@PathVariable Long paymentId) {
        return ApiResponse.ok(paymentService.get(paymentId));
    }

    @PutMapping("/api/admin/payments/{paymentId}/status")
    public ApiResponse<PaymentResponse> adminStatusLegacy(
            @PathVariable Long paymentId, @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return adminUpdateStatus(paymentId, request);
    }

    /// order-service gọi khi một đơn đã thanh toán bị huỷ (gateway chặn /internal từ ngoài).
    @PostMapping("/internal/payments/orders/{orderId}/refund")
    public ApiResponse<com.huynqb.laundrylocker.payment.dto.internal.OrderRefundResult> refundOrderInternal(
            @PathVariable Long orderId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Long actorUserId) {
        return ApiResponse.ok(
                "ORDER_REFUNDED", "Order payments refunded to wallet",
                paymentService.refundOrder(orderId, reason, actorUserId));
    }

    @GetMapping("/internal/payments/{id}")
    public ApiResponse<PaymentResponse> getInternal(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.get(id));
    }
}
