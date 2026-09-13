package com.huynqb.laundrylocker.order.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.order.dto.*;
import com.huynqb.laundrylocker.order.model.Promotion;
import com.huynqb.laundrylocker.order.service.DroneOrderMaintenanceService;
import com.huynqb.laundrylocker.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final DroneOrderMaintenanceService droneOrderMaintenanceService;
    private final OrderService orderService;

    @PostMapping("/api/orders")
    public ApiResponse<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok("ORDER_CREATED", "Order created", orderService.create(request));
    }

    @PostMapping("/api/orders/send")
    public ApiResponse<OrderResponse> createSend(
            @Valid @RequestBody SendOrderRequest request, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_CREATED", "Send order created", orderService.createSend(request, userId));
    }

    @PostMapping("/api/orders/rental")
    public ApiResponse<OrderResponse> createRental(
            @Valid @RequestBody RentalOrderRequest request, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_CREATED", "Rental order created", orderService.createRental(request, userId));
    }

    @PostMapping("/api/orders/drone-deliveries")
    public ApiResponse<DroneDeliveryOrderResponse> createDroneDelivery(
            @Valid @RequestBody CreateDroneDeliveryOrderRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.ok(
                "ORDER_CREATED",
                "Drone delivery order created",
                orderService.createDroneDelivery(request, userId, idempotencyKey));
    }

    @PostMapping("/api/orders/{orderId}/extend-rental")
    public ApiResponse<OrderResponse> extendRental(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-User-Id") Long userId) {
        int hours = Integer.parseInt(String.valueOf(request.getOrDefault("hours", "0")));
        if (hours < 1 || hours > 720) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "INVALID_REQUEST", "hours must be between 1 and 720");
        }
        return ApiResponse.ok("ORDER_RENTAL_EXTENDED", "Rental extended", orderService.extendRental(orderId, userId, hours));
    }

    @GetMapping("/api/orders/access/{code}")
    public ApiResponse<OrderResponse> getByAccess(@PathVariable String code) {
        return ApiResponse.ok(orderService.getByAccess(code));
    }

    @GetMapping("/internal/orders/by-access")
    public ApiResponse<OrderResponse> getByAccessInternal(@RequestParam String code) {
        return ApiResponse.ok(orderService.getByAccess(code));
    }

    @PatchMapping("/api/orders/{id}/status")
    public ApiResponse<OrderResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ApiResponse.ok("ORDER_STATUS_UPDATED", "Order status updated", orderService.updateStatus(id, request));
    }

    @PutMapping("/api/orders/{orderId}/confirm")
    public ApiResponse<OrderResponse> confirm(@PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_CONFIRMED", "Order confirmed", orderService.confirm(orderId, userId));
    }

    // Laundry lifecycle endpoints (collect/weight/process/ready/return) đã gỡ
    // 2026-07-03 — dự án không còn nghiệp vụ giặt ủi, chỉ SEND/RENTAL.

    @PutMapping("/api/orders/{orderId}/complete")
    public ApiResponse<OrderResponse> complete(@PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_COMPLETED", "Order completed", orderService.complete(orderId, userId));
    }

    @PutMapping("/api/orders/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancel(
            @PathVariable Long orderId,
            @RequestParam(required = false) Integer reason,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("ORDER_CANCELED", "Order canceled", orderService.cancel(orderId, reason, userId));
    }

    @PostMapping("/api/orders/{orderId}/reset-pin")
    public ApiResponse<OrderResponse> resetPin(@PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_PIN_RESET", "Order PIN reset", orderService.resetPin(orderId, userId));
    }

    @PostMapping("/api/orders/{orderId}/delegate")
    public ApiResponse<OrderResponse> delegate(
            @PathVariable Long orderId,
            @Valid @RequestBody DelegateOrderRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_DELEGATED", "Order pickup delegated", orderService.delegate(orderId, userId, request));
    }

    @PostMapping("/api/orders/{orderId}/checkout")
    public ApiResponse<OrderResponse> checkout(
            @PathVariable Long orderId,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "X-User-Id", required = false) Long staffId) {
        String note = request == null || request.get("note") == null ? null : String.valueOf(request.get("note"));
        return ApiResponse.ok("ORDER_CHECKED_OUT", "Order checked out", orderService.checkout(orderId, staffId, note));
    }

    @PostMapping("/api/orders/{orderId}/pickup-storage")
    public ApiResponse<OrderResponse> pickupStorage(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_PICKUP_STORAGE_OK", "Storage order picked up", orderService.pickupStorage(orderId, userId));
    }

    @PostMapping("/api/orders/{orderId}/report-box-fault")
    public ApiResponse<OrderResponse> reportBoxFault(
            @PathVariable Long orderId,
            @RequestBody(required = false) Map<String, String> request,
            @RequestHeader("X-User-Id") Long userId) {
        String reason = request == null ? null : request.get("reason");
        return ApiResponse.ok(
                "ORDER_BOX_FAULT_REPORTED",
                "Order box fault reported",
                orderService.reportBoxFault(orderId, userId, reason));
    }

    @PostMapping("/api/orders/{orderId}/reorder")
    public ApiResponse<OrderResponse> reorder(@PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_REORDERED", "Reorder created", orderService.reorder(orderId, userId));
    }

    @GetMapping("/api/orders/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(orderService.get(id));
    }

    @GetMapping("/api/orders/{orderId}/drone-delivery")
    public ApiResponse<DroneDeliveryOrderResponse> getDroneDelivery(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(orderService.getDroneDelivery(orderId, userId));
    }

    @GetMapping("/api/maintenance/drone-orders")
    public ApiResponse<List<DroneMissionResponse>> maintenanceDroneOrders(
            @RequestParam(required = false) String deliveryStage) {
        return ApiResponse.ok(droneOrderMaintenanceService.queue(deliveryStage));
    }

    @PostMapping("/api/maintenance/drone-orders/{orderId}/accept")
    public ResponseEntity<ApiResponse<DroneMissionResponse>> acceptDroneOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody AcceptDroneOrderRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.accepted()
                .body(
                        ApiResponse.ok(
                                "DRONE_ORDER_ACCEPTED",
                                "Drone order accepted",
                                droneOrderMaintenanceService.accept(orderId, userId, idempotencyKey, request)));
    }

    @PostMapping("/api/maintenance/drone-orders/{orderId}/launch")
    public ResponseEntity<ApiResponse<DroneMissionResponse>> launchDroneOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.accepted()
                .body(
                        ApiResponse.ok(
                                "DRONE_ORDER_LAUNCHING",
                                "Drone launch requested",
                                droneOrderMaintenanceService.launch(orderId, userId, idempotencyKey)));
    }

    @PostMapping("/api/maintenance/drone-orders/{orderId}/cancel")
    public ApiResponse<DroneMissionResponse> cancelDroneOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody CancelDroneOrderRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(
                "DRONE_ORDER_CANCELED",
                "Drone order canceled",
                droneOrderMaintenanceService.cancel(orderId, userId, request));
    }

    @GetMapping("/api/orders/{orderId}/status")
    public ApiResponse<OrderStatusResponse> status(@PathVariable Long orderId) {
        return ApiResponse.ok(orderService.status(orderId));
    }

    @GetMapping("/api/orders/code/{orderCode}")
    public ApiResponse<OrderResponse> getByCode(@PathVariable String orderCode) {
        return ApiResponse.ok(orderService.getByCode(orderCode));
    }

    @GetMapping("/api/orders/pin/{pinCode}")
    public ApiResponse<OrderResponse> getByPin(@PathVariable String pinCode) {
        return ApiResponse.ok(orderService.getByPin(pinCode));
    }

    @GetMapping("/api/orders")
    public ApiResponse<List<OrderResponse>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long staffId) {
        return ApiResponse.ok(userId == null ? orderService.list(status, staffId) : orderService.listByUser(userId));
    }

    @GetMapping("/api/orders/my-orders")
    public ApiResponse<List<OrderResponse>> myOrders(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(orderService.listByUser(userId));
    }

    @PostMapping("/api/orders/{orderId}/rate")
    public ApiResponse<OrderRatingResponse> rate(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderRatingRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_RATED", "Order rated", orderService.rate(orderId, request, userId));
    }

    @GetMapping("/api/orders/{orderId}/rating")
    public ApiResponse<OrderRatingResponse> rating(@PathVariable Long orderId) {
        return ApiResponse.ok(orderService.rating(orderId));
    }

    @GetMapping("/api/orders/my-ratings")
    public ApiResponse<List<OrderRatingResponse>> myRatings(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(orderService.myRatings(userId));
    }

    @PostMapping("/api/orders/{orderId}/complaint")
    public ApiResponse<OrderComplaintResponse> complaint(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderComplaintRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("COMPLAINT_CREATED", "Complaint created", orderService.complain(orderId, request, userId));
    }

    @GetMapping("/api/orders/{orderId}/complaints")
    public ApiResponse<List<OrderComplaintResponse>> complaints(@PathVariable Long orderId) {
        return ApiResponse.ok(orderService.complaints(orderId));
    }

    @GetMapping("/api/orders/my-complaints")
    public ApiResponse<List<OrderComplaintResponse>> myComplaints(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(orderService.myComplaints(userId));
    }

    @GetMapping("/api/orders/{orderId}/timeline")
    public ApiResponse<List<OrderTimelineEvent>> timeline(@PathVariable Long orderId) {
        return ApiResponse.ok(orderService.timeline(orderId));
    }

    @GetMapping("/internal/orders/{id}")
    public ApiResponse<OrderSummary> getInternal(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getSummary(id));
    }

    @GetMapping("/api/admin/orders")
    public ApiResponse<List<OrderResponse>> adminOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(orderService.list(status, type, null));
    }

    @GetMapping("/api/admin/orders/{id}")
    public ApiResponse<OrderResponse> adminOrder(@PathVariable Long id) {
        return get(id);
    }

    @PutMapping("/api/admin/orders/{id}/status")
    public ApiResponse<OrderResponse> adminStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return updateStatus(id, request);
    }

    @GetMapping("/api/admin/dashboard/overview")
    public ApiResponse<java.util.Map<String, Object>> dashboard() {
        return ApiResponse.ok(orderService.statistics());
    }

    @GetMapping("/api/admin/orders/statistics")
    public ApiResponse<Map<String, Object>> orderStatistics() {
        return ApiResponse.ok(orderService.statistics());
    }

    @GetMapping("/api/admin/orders/revenue")
    public ApiResponse<Map<String, Object>> revenue() {
        return ApiResponse.ok(orderService.revenue());
    }

    @PostMapping("/api/admin/promotions")
    public ApiResponse<Promotion> createPromotion(
            @Valid @RequestBody PromotionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("PROMOTION_CREATED", "Promotion created", orderService.createPromotion(request, userId));
    }

    @GetMapping("/api/admin/promotions")
    public ApiResponse<List<Promotion>> promotions(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(orderService.promotions(code, status));
    }

    @GetMapping("/api/admin/promotions/{promotionId}")
    public ApiResponse<Promotion> promotion(@PathVariable Long promotionId) {
        return ApiResponse.ok(orderService.promotion(promotionId));
    }

    @PutMapping("/api/admin/promotions/{promotionId}")
    public ApiResponse<Promotion> updatePromotion(
            @PathVariable Long promotionId, @Valid @RequestBody PromotionRequest request) {
        return ApiResponse.ok("PROMOTION_UPDATED", "Promotion updated", orderService.updatePromotion(promotionId, request));
    }

    @DeleteMapping("/api/admin/promotions/{promotionId}")
    public ApiResponse<Void> deletePromotion(@PathVariable Long promotionId) {
        orderService.deletePromotion(promotionId);
        return ApiResponse.ok("PROMOTION_DELETED", "Promotion deleted");
    }

    @GetMapping("/api/admin/promotions/status/{status}")
    public ApiResponse<List<Promotion>> promotionsByStatus(@PathVariable String status) {
        return ApiResponse.ok(orderService.promotionsByStatus(status));
    }

    @GetMapping("/api/admin/promotions/search")
    public ApiResponse<List<Promotion>> searchPromotions(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(orderService.searchPromotions(keyword));
    }

    @GetMapping("/api/admin/promotions/validate/{code}")
    public ApiResponse<Map<String, Object>> adminValidatePromotion(@PathVariable String code) {
        return ApiResponse.ok(orderService.validatePromotion(code));
    }

    @GetMapping("/api/promotions/active")
    public ApiResponse<List<Promotion>> activePromotions() {
        return ApiResponse.ok(orderService.activePromotions());
    }

    @GetMapping("/api/admin/promotions/active")
    public ApiResponse<List<Promotion>> adminActivePromotions() {
        return activePromotions();
    }

    // Validate mã cho client: lockerId để check scope theo tủ; X-User-Id (gateway
    // gắn khi có JWT) để check lượt dùng còn lại của chính user đó.
    @GetMapping("/api/promotions/validate/{code}")
    public ApiResponse<Map<String, Object>> validatePromotion(
            @PathVariable String code,
            @RequestParam(required = false) Long lockerId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(orderService.validatePromotion(code, lockerId, userId));
    }

    // ---- Ví voucher của user (lưu mã -> dùng khi đặt đơn) ----

    @PostMapping("/api/promotions/{promotionId}/claim")
    public ApiResponse<Map<String, Object>> claimPromotion(
            @PathVariable Long promotionId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(
                "PROMOTION_CLAIMED", "Promotion saved to wallet",
                orderService.claimPromotion(promotionId, userId));
    }

    @GetMapping("/api/promotions/vouchers/my")
    public ApiResponse<List<Map<String, Object>>> myVouchers(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(orderService.myVouchers(userId, status));
    }

    @GetMapping("/internal/orders/stores/{storeId}/ratings")
    public ApiResponse<List<Map<String, Object>>> storeRatings(@PathVariable Long storeId) {
        return ApiResponse.ok(orderService.storeRatings(storeId));
    }

    @PostMapping("/api/admin/scheduler/auto-cancel")
    public ApiResponse<Map<String, Object>> autoCancel() {
        return ApiResponse.ok("SCHEDULER_AUTO_CANCEL_OK", "Auto cancel job completed", orderService.autoCancelUnconfirmedOrders());
    }

    @PostMapping("/api/admin/scheduler/release-boxes")
    public ApiResponse<Map<String, Object>> releaseBoxes() {
        return ApiResponse.ok("SCHEDULER_RELEASE_BOXES_OK", "Box release job completed", orderService.releaseBoxesAfterCompletion());
    }

    @PostMapping("/api/admin/scheduler/pickup-reminders")
    public ApiResponse<Map<String, Object>> pickupReminders() {
        return ApiResponse.ok("SCHEDULER_PICKUP_REMINDERS_OK", "Pickup reminder job completed", orderService.pickupReminders());
    }

    @PostMapping("/api/admin/scheduler/release-overdue")
    public ApiResponse<Map<String, Object>> releaseOverdue() {
        return ApiResponse.ok(
                "SCHEDULER_RELEASE_OVERDUE_OK", "Overdue release job completed", orderService.releaseOverdueOrders());
    }

    @PostMapping("/api/admin/scheduler/reconcile-boxes")
    public ApiResponse<Map<String, Object>> reconcileBoxes() {
        return ApiResponse.ok(
                "SCHEDULER_RECONCILE_BOXES_OK", "Box reconcile job completed", orderService.reconcileBoxStates());
    }

    @GetMapping("/api/admin/scheduler/status")
    public ApiResponse<Map<String, Object>> schedulerStatus() {
        return ApiResponse.ok(Map.of("enabled", true, "owner", "order-service"));
    }
}
