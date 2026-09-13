package com.huynqb.laundrylocker.payment.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.payment.dto.*;
import com.huynqb.laundrylocker.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/payments/topup/create")
    public ApiResponse<TopupResponse> createTopup(
            @Valid @RequestBody CreateTopupRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("TOPUP_URL_CREATED", "Topup URL created", paymentService.createTopupUrl(userId, request));
    }

    // Alias callback path that matches what the mobile WebView detects
    @GetMapping("/payments/vnpay/callback")
    public ApiResponse<PaymentResponse> vnpayCallback(@RequestParam Map<String, String> params) {
        return ApiResponse.ok("PAYMENT_RETURN_PROCESSED", "VNPay callback processed", paymentService.handleVnPayReturn(params));
    }

    @PostMapping("/api/payments/checkout")
    public ApiResponse<PaymentResponse> checkout(
            @Valid @RequestBody CheckoutRequest request, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("PAYMENT_CHECKOUT", "Checkout processed", paymentService.checkout(userId, request));
    }

    @PostMapping("/api/payments")
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        return ApiResponse.ok("PAYMENT_CREATED", "Payment created", paymentService.create(request));
    }

    @PostMapping("/api/payments/create")
    public ApiResponse<PaymentResponse> createLegacy(@Valid @RequestBody CreatePaymentRequest request) {
        return create(request);
    }

    @PatchMapping("/api/payments/{id}/status")
    public ApiResponse<PaymentResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return ApiResponse.ok("PAYMENT_STATUS_UPDATED", "Payment status updated", paymentService.updateStatus(id, request));
    }

    @GetMapping("/api/payments/{id}")
    public ApiResponse<PaymentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.get(id));
    }

    @GetMapping("/api/payments")
    public ApiResponse<List<PaymentResponse>> listByOrder(@RequestParam Long orderId) {
        return ApiResponse.ok(paymentService.listByOrder(orderId));
    }

    @GetMapping("/api/payments/order/{orderId}")
    public ApiResponse<List<PaymentResponse>> listByOrderLegacy(@PathVariable Long orderId) {
        return ApiResponse.ok(paymentService.listByOrder(orderId));
    }

    @GetMapping("/api/payments/vnpay/return")
    public ApiResponse<PaymentResponse> vnpayReturn(@RequestParam Map<String, String> params) {
        return ApiResponse.ok("PAYMENT_RETURN_PROCESSED", "VNPay return processed", paymentService.handleVnPayReturn(params));
    }

    @GetMapping("/api/payments/vnpay/ipn")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        paymentService.handleVnPayReturn(params);
        return Map.of("RspCode", "00", "Message", "Success");
    }

    @PostMapping("/api/payments/momo/callback")
    public ApiResponse<PaymentResponse> momoCallback(@RequestBody Map<String, Object> params) {
        Map<String, String> stringParams = new HashMap<>();
        params.forEach((k, v) -> stringParams.put(k, v == null ? null : v.toString()));
        return ApiResponse.ok(
                "MOMO_CALLBACK_PROCESSED", "MoMo callback processed", paymentService.handleMomoCallback(stringParams));
    }

    @GetMapping("/api/payments/momo/return")
    public ApiResponse<PaymentResponse> momoReturn(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(
                "MOMO_RETURN_PROCESSED", "MoMo return processed", paymentService.handleMomoCallback(params));
    }

    @PostMapping("/api/payments/{paymentId}/refund")
    public ApiResponse<RefundResponse> refund(
            @PathVariable Long paymentId,
            @Valid @RequestBody RefundRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("REFUND_CREATED", "Refund created", paymentService.refund(paymentId, request, userId));
    }

    @GetMapping("/api/payments/order/{orderId}/refunds")
    public ApiResponse<List<RefundResponse>> refunds(@PathVariable Long orderId) {
        return ApiResponse.ok(paymentService.refundsByOrder(orderId));
    }

    @GetMapping("/api/payments/refund/{refundId}")
    public ApiResponse<RefundResponse> refundStatus(@PathVariable Long refundId) {
        return ApiResponse.ok(paymentService.getRefund(refundId));
    }

    @GetMapping("/api/admin/payments")
    public ApiResponse<List<PaymentResponse>> adminList() {
        return ApiResponse.ok(paymentService.listAll());
    }

    @PatchMapping("/api/admin/payments/{id}/status")
    public ApiResponse<PaymentResponse> adminStatus(
            @PathVariable Long id, @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return updateStatus(id, request);
    }

    @GetMapping("/api/admin/payments/{paymentId}")
    public ApiResponse<PaymentResponse> adminGet(@PathVariable Long paymentId) {
        return get(paymentId);
    }

    @PutMapping("/api/admin/payments/{paymentId}/status")
    public ApiResponse<PaymentResponse> adminStatusLegacy(
            @PathVariable Long paymentId, @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return updateStatus(paymentId, request);
    }

    @GetMapping("/internal/payments/{id}")
    public ApiResponse<PaymentResponse> getInternal(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.get(id));
    }
}
