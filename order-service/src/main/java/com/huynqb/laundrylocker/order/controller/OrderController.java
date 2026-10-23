package com.huynqb.laundrylocker.order.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.OrderSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.media.MediaUpload;
import com.huynqb.laundrylocker.order.dto.BoxIncidentNotice;
import com.huynqb.laundrylocker.order.dto.*;
import com.huynqb.laundrylocker.order.service.DroneOrderMaintenanceService;
import com.huynqb.laundrylocker.order.service.DroneDeliveryQueryService;
import com.huynqb.laundrylocker.order.service.DroneWeatherQueryService;
import com.huynqb.laundrylocker.order.service.DroneMissionProgressService;
import com.huynqb.laundrylocker.order.model.DroneFailureReasons;
import com.huynqb.laundrylocker.order.service.DroneDeliveryFailureService;
import com.huynqb.laundrylocker.order.service.DroneOrderBookingService;
import com.huynqb.laundrylocker.order.service.DroneParcelService;
import com.huynqb.laundrylocker.order.service.DronePickupService;
import com.huynqb.laundrylocker.order.service.DroneRefundService;
import com.huynqb.laundrylocker.order.service.OrderAccessGuard;
import com.huynqb.laundrylocker.order.service.OrderService;
import com.huynqb.laundrylocker.order.service.PromotionImageService;
import com.huynqb.laundrylocker.order.service.ReceiverOrderQueryService;
import com.huynqb.laundrylocker.order.service.StoreRatingQueryService;
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
    private final DroneDeliveryQueryService droneDeliveryQueryService;
    private final DroneMissionProgressService droneMissionProgressService;
    private final DroneRefundService droneRefundService;
    private final DroneParcelService droneParcelService;
    private final DroneOrderBookingService droneOrderBookingService;
    private final DronePickupService dronePickupService;
    private final DroneDeliveryFailureService droneDeliveryFailureService;
    private final DroneWeatherQueryService droneWeatherQueryService;
    private final OrderAccessGuard orderAccess;
    private final OrderService orderService;
    private final PromotionImageService promotionImageService;
    private final ReceiverOrderQueryService receiverOrderQueryService;
    private final StoreRatingQueryService storeRatingQueryService;

    // Đã gỡ (SEC-03/05): `POST /api/orders` (userId lấy từ body, đơn 0đ tự PAID),
    // `PATCH /api/orders/{id}/status`, `POST /api/orders/{id}/checkout`, `GET /api/orders`,
    // `GET /api/orders/code|pin|access/...`. Admin đổi trạng thái qua /api/admin/orders/{id}/status;
    // kiosk tra mã qua iot-service (/internal/orders/by-access).

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
                droneOrderBookingService.create(request, userId, idempotencyKey));
    }

    /// Trước khi đặt: từ điểm lấy hàng này tới tủ nhận kia thì drone cất cánh ở tủ nào, phí bao nhiêu.
    @PostMapping("/api/orders/drone-deliveries/quote")
    public ApiResponse<DroneDeliveryQuoteResponse> quoteDroneDelivery(
            @Valid @RequestBody DroneDeliveryQuoteRequest request) {
        return ApiResponse.ok(droneOrderBookingService.quote(request));
    }

    @PostMapping("/api/orders/{orderId}/extend-rental")
    public ApiResponse<OrderResponse> extendRental(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-User-Id") Long userId) {
        int hours;
        try {
            hours = Integer.parseInt(String.valueOf(request.getOrDefault("hours", "0")));
        } catch (NumberFormatException ex) {
            throw new BusinessException("INVALID_REQUEST", "hours must be a whole number");
        }
        // Giới hạn trên theo cấu hình admin (app.order.extend-max-hours) — kiểm trong OrderService.
        return ApiResponse.ok("ORDER_RENTAL_EXTENDED", "Rental extended", orderService.extendRental(orderId, userId, hours));
    }

    @PostMapping("/api/orders/{orderId}/assess-overtime")
    public ApiResponse<OrderResponse> assessOvertime(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_OVERTIME_ASSESSED", "Overtime fee assessed", orderService.assessOvertime(orderId, userId));
    }

    @GetMapping("/internal/orders/by-access")
    public ApiResponse<OrderResponse> getByAccessInternal(@RequestParam String code) {
        return ApiResponse.ok(orderService.getByAccess(code));
    }

    // iot-service báo ô của đơn đã được mở để bỏ hàng (chặn ở gateway như mọi /internal).
    @PostMapping("/internal/orders/{id}/drop-opened")
    public ApiResponse<Void> dropOpened(@PathVariable Long id) {
        orderService.markDropOpened(id);
        return ApiResponse.ok("ORDER_DROP_OPENED", "Drop-off opening recorded");
    }

    @GetMapping("/internal/orders/active-by-box/{boxId}")
    public ApiResponse<ActiveBoxOrderResponse> getActiveOrderByBox(@PathVariable Long boxId) {
        return ApiResponse.ok(orderService.getActiveOrderByBox(boxId));
    }

    @GetMapping("/internal/orders/drone-technicians/busy-ids")
    public ApiResponse<java.util.Set<Long>> getBusyDroneTechnicianIds() {
        return ApiResponse.ok(dronePickupService.getBusyDroneTechnicianIds());
    }

    @PostMapping("/internal/orders/{id}/relocate-box")
    public ApiResponse<OrderResponse> relocateBox(
            @PathVariable Long id,
            @RequestParam Long newBoxId,
            @RequestParam(required = false) Integer newBoxNumber) {
        return ApiResponse.ok("ORDER_RELOCATED", "Order relocated to new box", orderService.relocateBox(id, newBoxId, newBoxNumber));
    }

    @PostMapping("/internal/orders/{id}/direct-handover")
    public ApiResponse<OrderResponse> directHandover(
            @PathVariable Long id,
            @RequestParam(required = false) String otp) {
        return ApiResponse.ok("ORDER_HANDED_OVER", "Order handed over directly to customer", orderService.directHandover(id, otp));
    }

    @PostMapping("/internal/orders/{id}/hub-escrow")
    public ApiResponse<OrderResponse> hubEscrow(
            @PathVariable Long id,
            @RequestParam String sealNumber) {
        return ApiResponse.ok("ORDER_HUB_ESCROWED", "Order escrowed to hub", orderService.hubEscrow(id, sealNumber));
    }

    /// locker-service gọi khi KTV xử lý xong sự cố ô có hàng (sửa tại chỗ, kiểm tra không lỗi, hoàn tất
    /// phiếu): mở lại việc lấy đồ, bù thời gian chờ vào hạn. Gọi lặp an toàn.
    /// locker-service gọi khi ô có hàng của đơn bị báo sự cố qua đường không đi qua order-service.
    /// Body (tuỳ chọn): lúc báo, đã xác nhận chưa, người báo — xem [BoxIncidentNotice]. Không body ⇒ như bản cũ.
    @PostMapping("/internal/orders/{id}/box-incident-opened")
    public ApiResponse<OrderResponse> boxIncidentOpened(
            @PathVariable Long id,
            @RequestBody(required = false) BoxIncidentNotice notice) {
        return ApiResponse.ok("ORDER_BOX_INCIDENT_OPENED", "Box incident opened",
                orderService.boxIncidentOpened(id, notice));
    }

    @PostMapping("/internal/orders/{id}/box-incident-resolved")
    public ApiResponse<OrderResponse> boxIncidentResolved(
            @PathVariable Long id,
            @RequestParam(required = false) String note) {
        return ApiResponse.ok("ORDER_BOX_INCIDENT_RESOLVED", "Box incident resolved",
                orderService.resumeAfterBoxIncident(id, null, note));
    }

    @PostMapping("/internal/orders/{id}/incident-note")
    public ApiResponse<Void> addIncidentNote(
            @PathVariable Long id,
            @RequestParam String note) {
        orderService.addIncidentHistory(id, note);
        return ApiResponse.ok(null);
    }

    @PostMapping("/internal/orders/{id}/payment-status")
    public ApiResponse<Void> updatePaymentStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Long actorUserId) {
        orderService.updatePaymentStatus(id, status, note, actorUserId);
        return ApiResponse.ok(null);
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
        OrderResponse canceled = orderService.cancel(orderId, reason, userId);
        if ("DRONE_DELIVERY".equalsIgnoreCase(canceled.type())) {
            // KTV drone đang được mời/đang tới lấy hàng không còn việc này nữa.
            dronePickupService.closeForOrder(orderId);
        }
        // Đơn drone đã trả tiền mà huỷ trước khi bay ⇒ tạo yêu cầu hoàn tiền. Chạy sau khi huỷ
        // đã commit; tạo được thì trả lại bản đơn mới nhất (paymentStatus = REFUND_PENDING).
        if (droneRefundService.refundCanceledOrder(orderId, userId)) {
            canceled = orderService.get(orderId);
        }
        return ApiResponse.ok("ORDER_CANCELED", "Order canceled", canceled);
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

    @PostMapping("/api/orders/{orderId}/pickup-storage")
    public ApiResponse<OrderResponse> pickupStorage(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_PICKUP_STORAGE_OK", "Storage order picked up", orderService.pickupStorage(orderId, userId));
    }

    @PostMapping("/api/orders/{orderId}/report-box-fault")
    public ApiResponse<OrderResponse> reportBoxFault(
            @PathVariable Long orderId,
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader("X-User-Id") Long userId) {
        Object reason = request == null ? null : request.get("reason");
        Object attachments = request == null ? null : request.get("attachments");
        if (attachments != null && !(attachments instanceof List<?>)) {
            throw new BusinessException("ATTACHMENTS_INVALID", "attachments must be an array");
        }
        @SuppressWarnings("unchecked")
        List<Object> attachmentList = (List<Object>) attachments;
        return ApiResponse.ok(
                "ORDER_BOX_FAULT_REPORTED",
                "Order box fault reported",
                orderService.reportBoxFault(orderId, userId, reason == null ? null : String.valueOf(reason), attachmentList));
    }

    @PostMapping("/api/orders/{orderId}/reorder")
    public ApiResponse<OrderResponse> reorder(@PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_REORDERED", "Reorder created", orderService.reorder(orderId, userId));
    }

    @GetMapping("/api/orders/{id}")
    public ApiResponse<OrderResponse> get(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        orderAccess.requireOwner(id, userId, roles);
        return ApiResponse.ok(orderService.get(id));
    }

    @GetMapping("/api/orders/{orderId}/drone-delivery")
    public ApiResponse<DroneDeliveryOrderResponse> getDroneDelivery(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(droneDeliveryQueryService.get(orderId, userId));
    }

    /// Người gửi xác nhận đã bỏ kiện vào ô DRONE ở tủ gửi — điều kiện để đội bay tiếp nhận.
    @PostMapping("/api/orders/{orderId}/drone-delivery/drop-confirmation")
    public ApiResponse<DroneDeliveryOrderResponse> confirmDroneParcelDrop(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        droneParcelService.confirmDrop(orderId, userId);
        return ApiResponse.ok(
                "DRONE_PARCEL_DROPPED", "Parcel drop-off confirmed", droneDeliveryQueryService.get(orderId, userId));
    }

    /// Khách từ chối trả phụ thu cân lệch: huỷ đơn, hoàn phần đã trả, đội bay trả lại kiện.
    @PostMapping("/api/orders/{orderId}/drone-delivery/decline-surcharge")
    public ApiResponse<DroneDeliveryOrderResponse> declineDroneSurcharge(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        droneOrderMaintenanceService.cancelUnpaidSurcharge(orderId, userId, null);
        droneRefundService.refundCanceledOrder(orderId, userId);
        return ApiResponse.ok(
                "DRONE_SURCHARGE_DECLINED",
                "Drone order canceled after declining the weight surcharge",
                droneDeliveryQueryService.get(orderId, userId));
    }

    /// Đơn không giao được: đội bay xác nhận đã trả kiện cho người gửi.
    @PostMapping("/api/drone-technician/drone-orders/{orderId}/parcel-return")
    public ApiResponse<DroneDeliveryOrderResponse> confirmDroneParcelReturn(
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) DroneParcelReturnRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        droneParcelService.confirmReturn(orderId, userId, false, request == null ? null : request.note());
        return ApiResponse.ok(
                "DRONE_PARCEL_RETURNED",
                "Parcel returned to the sender",
                droneDeliveryQueryService.getForTechnician(orderId, userId));
    }

    @PostMapping("/api/admin/drone-orders/{orderId}/parcel-return")
    public ApiResponse<DroneDeliveryOrderResponse> adminConfirmDroneParcelReturn(
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) DroneParcelReturnRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        droneParcelService.confirmReturn(orderId, userId, true, request == null ? null : request.note());
        return ApiResponse.ok(
                "DRONE_PARCEL_RETURNED", "Parcel returned to the sender", droneDeliveryQueryService.getForAdmin(orderId));
    }

    @GetMapping("/api/drone-technician/drone-orders")
    public ApiResponse<List<DroneDeliveryOrderResponse>> maintenanceDroneOrders(
            @RequestParam(required = false) String deliveryStage,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(droneDeliveryQueryService.operations(deliveryStage, userId, false));
    }

    @GetMapping("/api/drone-technician/drone-orders/{orderId}")
    public ApiResponse<DroneDeliveryOrderResponse> maintenanceDroneOrder(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(droneDeliveryQueryService.getForTechnician(orderId, userId));
    }

    // Admin xem lại cả hành trình đã kết thúc; includeFinished=false chỉ lấy nhiệm vụ đang chạy.
    // droneUnitId (tuỳ chọn) lọc theo drone được gán cho nhiệm vụ — trang chi tiết drone.
    @GetMapping("/api/admin/drone-orders")
    public ApiResponse<List<DroneDeliveryOrderResponse>> adminDroneOrders(
            @RequestParam(required = false) String deliveryStage,
            @RequestParam(defaultValue = "true") boolean includeFinished,
            @RequestParam(required = false) Long droneUnitId) {
        return ApiResponse.ok(droneDeliveryQueryService.operations(deliveryStage, null, true, includeFinished, droneUnitId));
    }

    @GetMapping("/api/admin/drone-orders/{orderId}")
    public ApiResponse<DroneDeliveryOrderResponse> adminDroneOrder(@PathVariable Long orderId) {
        return ApiResponse.ok(droneDeliveryQueryService.getForAdmin(orderId));
    }

    // ---- Thời tiết: khách, KTV, admin cùng xem một kết quả với bước kiểm tra lúc đặt đơn / phóng drone.

    /// Người gửi / người nhận xem thời tiết tuyến bay của đơn mình.
    @GetMapping("/api/orders/{orderId}/drone-delivery/weather")
    public ApiResponse<DroneWeatherViews.RouteWeather> droneDeliveryWeather(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        droneDeliveryQueryService.get(orderId, userId);
        return ApiResponse.ok(droneWeatherQueryService.forOrder(orderId));
    }

    @GetMapping("/api/drone-technician/drone-orders/{orderId}/weather")
    public ApiResponse<DroneWeatherViews.RouteWeather> technicianDroneOrderWeather(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        droneDeliveryQueryService.getForTechnician(orderId, userId);
        return ApiResponse.ok(droneWeatherQueryService.forOrder(orderId));
    }

    @GetMapping("/api/admin/drone-orders/{orderId}/weather")
    public ApiResponse<DroneWeatherViews.RouteWeather> adminDroneOrderWeather(@PathVariable Long orderId) {
        return ApiResponse.ok(droneWeatherQueryService.forOrder(orderId));
    }

    /// Thời tiết tại từng tủ có bãi đáp — KTV và admin điều phối trước khi nhận / phóng đơn.
    @GetMapping("/api/drone-technician/drone-orders/weather/lockers")
    public ApiResponse<List<DroneWeatherViews.LockerWeather>> technicianLockerWeather() {
        return ApiResponse.ok(droneWeatherQueryService.droneLockers());
    }

    @GetMapping("/api/admin/drone-orders/weather/lockers")
    public ApiResponse<List<DroneWeatherViews.LockerWeather>> adminLockerWeather() {
        return ApiResponse.ok(droneWeatherQueryService.droneLockers());
    }

    @PostMapping("/api/drone-technician/drone-orders/{orderId}/accept")
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

    @PostMapping("/api/drone-technician/drone-orders/{orderId}/loading-confirmation")
    public ApiResponse<DroneMissionResponse> confirmDroneLoading(
            @PathVariable Long orderId,
            @Valid @RequestBody ConfirmDroneLoadingRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.ok(
                "DRONE_LOADING_CONFIRMED",
                "Drone loading checklist confirmed",
                droneOrderMaintenanceService.confirmLoading(orderId, userId, idempotencyKey, request));
    }
    @PostMapping("/api/drone-technician/drone-orders/{orderId}/launch")
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

    @PostMapping("/api/drone-technician/drone-orders/{orderId}/cancel")
    public ApiResponse<DroneMissionResponse> cancelDroneOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody CancelDroneOrderRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        DroneMissionResponse canceled = droneOrderMaintenanceService.cancel(orderId, userId, request);
        droneRefundService.refundCanceledOrder(orderId, userId);
        return ApiResponse.ok("DRONE_ORDER_CANCELED", "Drone order canceled", canceled);
    }

    /**
     * KTV drone đang giữ đơn xác nhận đơn "giao không hoàn tất": đơn đang bị sự cố chặn mà không khắc
     * phục được, hoặc sự cố lúc bay. Đóng đơn, nhả drone/ô nhận, báo người gửi (và người nhận), rồi tạo
     * yêu cầu hoàn tiền sau khi việc đóng đơn đã commit.
     */
    @PostMapping("/api/drone-technician/drone-orders/{orderId}/fail")
    public ApiResponse<DroneDeliveryOrderResponse> failDroneOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) DroneDeliveryFailRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        droneDeliveryFailureService.fail(
                orderId, userId, DroneDeliveryFailureService.Actor.TECHNICIAN,
                failureReason(request), request == null ? null : request.note(), null);
        droneRefundService.refundCanceledOrder(orderId, userId);
        return ApiResponse.ok(
                "DRONE_DELIVERY_FAILED",
                "Drone delivery closed as not completed",
                droneDeliveryQueryService.getForTechnician(orderId, userId));
    }

    /// Như trên cho admin, với bất kỳ đơn drone nào chưa vào ô nhận.
    @PostMapping("/api/admin/drone-orders/{orderId}/fail")
    public ApiResponse<DroneDeliveryOrderResponse> adminFailDroneOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) DroneDeliveryFailRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        droneDeliveryFailureService.fail(
                orderId, userId, DroneDeliveryFailureService.Actor.ADMIN,
                failureReason(request), request == null ? null : request.note(), null);
        droneRefundService.refundCanceledOrder(orderId, userId);
        return ApiResponse.ok(
                "DRONE_DELIVERY_FAILED",
                "Drone delivery closed as not completed",
                droneDeliveryQueryService.getForAdmin(orderId));
    }

    private static String failureReason(DroneDeliveryFailRequest request) {
        if (request == null) {
            return null;
        }
        if (org.springframework.util.StringUtils.hasText(request.reason())) {
            return request.reason().trim().toUpperCase();
        }
        return request.reasonCode() == null ? null : DroneFailureReasons.fromLegacyCancelCode(request.reasonCode());
    }

    // ------------------------------------------------------------------ lấy hàng tận nhà

    /// KTV drone xem mình đang trong ca không, vị trí gần nhất và có đang bận không.
    @GetMapping("/api/drone-technician/drone-orders/presence")
    public ApiResponse<DronePickupService.Presence> droneTechnicianPresence(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(dronePickupService.presence(userId));
    }

    /// KTV drone vào/ra ca và gửi vị trí — cơ sở để hệ thống mời người rảnh gần người gửi nhất.
    @PutMapping("/api/drone-technician/drone-orders/presence")
    public ApiResponse<DronePickupService.Presence> updateDroneTechnicianPresence(
            @Valid @RequestBody DroneTechnicianPresenceRequest request, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(
                "DRONE_TECHNICIAN_PRESENCE_UPDATED",
                "Presence updated",
                dronePickupService.updatePresence(userId, request.onDuty(), request.latitude(), request.longitude()));
    }

    @PostMapping("/api/drone-technician/drone-orders/{orderId}/pickup/accept")
    public ApiResponse<DroneDeliveryOrderResponse> acceptDronePickup(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        dronePickupService.accept(orderId, userId);
        return ApiResponse.ok(
                "DRONE_PICKUP_ACCEPTED", "Pickup accepted", droneDeliveryQueryService.getForTechnician(orderId, userId));
    }

    @PostMapping("/api/drone-technician/drone-orders/{orderId}/pickup/decline")
    public ApiResponse<Void> declineDronePickup(@PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        dronePickupService.decline(orderId, userId);
        return ApiResponse.ok("DRONE_PICKUP_DECLINED", "Pickup declined", null);
    }

    /// KTV đã cầm kiện tại nhà người gửi (đúng mã lấy hàng, đúng kiện đã khai báo).
    @PostMapping("/api/drone-technician/drone-orders/{orderId}/pickup/confirm")
    public ApiResponse<DroneDeliveryOrderResponse> confirmDronePickup(
            @PathVariable Long orderId,
            @Valid @RequestBody DronePickupConfirmRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        dronePickupService.confirmPickedUp(
                orderId, userId, request.pickupCode(), request.parcelMatched(), request.note(), request.pickupPhoto());
        return ApiResponse.ok(
                "DRONE_PARCEL_PICKED_UP", "Parcel picked up", droneDeliveryQueryService.getForTechnician(orderId, userId));
    }

    /// KTV tới nơi nhưng không lấy được kiện: đơn giao không hoàn tất, hoàn tiền.
    @PostMapping("/api/drone-technician/drone-orders/{orderId}/pickup/fail")
    public ApiResponse<DroneDeliveryOrderResponse> failDronePickup(
            @PathVariable Long orderId,
            @Valid @RequestBody DroneDeliveryFailRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        dronePickupService.failPickup(orderId, userId, failureReason(request), request.note());
        droneRefundService.refundCanceledOrder(orderId, userId);
        return ApiResponse.ok(
                "DRONE_PICKUP_FAILED", "Pickup reported as failed", droneDeliveryQueryService.getForAdmin(orderId));
    }

    /// Drone đã gán gặp sự cố trước khi phóng: đổi sang drone rảnh khác tại tủ gửi.
    @PostMapping("/api/drone-technician/drone-orders/{orderId}/change-drone")
    public ApiResponse<DroneMissionResponse> changeDroneOfOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody AcceptDroneOrderRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(
                "DRONE_CHANGED", "Drone changed", droneOrderMaintenanceService.changeDrone(orderId, userId, request));
    }

    /// Admin xem KTV drone nào đang trong ca, rảnh và gần điểm lấy hàng để gán tay.
    @GetMapping("/api/admin/drone-orders/{orderId}/pickup/candidates")
    public ApiResponse<List<DroneTechnicianCandidate>> dronePickupCandidates(@PathVariable Long orderId) {
        return ApiResponse.ok(dronePickupService.candidates(orderId));
    }

    @PostMapping("/api/admin/drone-orders/{orderId}/pickup/assign")
    public ApiResponse<DroneDeliveryOrderResponse> assignDronePickup(
            @PathVariable Long orderId,
            @Valid @RequestBody AssignDronePickupRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        dronePickupService.assignByAdmin(orderId, request.technicianId(), userId);
        return ApiResponse.ok(
                "DRONE_PICKUP_ASSIGNED", "Technician assigned", droneDeliveryQueryService.getForAdmin(orderId));
    }

    /// Phiếu sự cố này đang chặn hoặc đã làm hỏng những đơn drone nào — cho admin và KTV xử lý phiếu.
    @GetMapping({
        "/api/admin/drone-orders/by-ticket/{ticketId}",
        "/api/drone-technician/drone-orders/by-ticket/{ticketId}",
        "/api/locker-technician/drone-orders/by-ticket/{ticketId}"
    })
    public ApiResponse<List<DroneDeliveryOrderResponse>> droneOrdersByTicket(@PathVariable Long ticketId) {
        return ApiResponse.ok(droneDeliveryQueryService.byTicket(ticketId));
    }

    /// Đơn STANDARD chưa có telemetry tự báo chặng: điều phối viên đã tiếp nhận xác nhận
    /// drone sang chặng kế tiếp (rời trạm → trên đường → sắp tới → đã tới → hàng vào ô).
    /// Body tuỳ chọn `{expectedStage}`: chặng app đang hiển thị. Telemetry đã đẩy chặng đi trước
    /// ⇒ 409 DRONE_STAGE_CHANGED thay vì nhảy thêm một chặng.
    @PostMapping("/api/drone-technician/drone-orders/{orderId}/advance")
    public ApiResponse<DroneDeliveryOrderResponse> advanceDroneOrder(
            @PathVariable Long orderId,
            @RequestBody(required = false) AdvanceDroneOrderRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        droneMissionProgressService.advanceByOperator(
                orderId, userId, request == null ? null : request.expectedStage());
        return ApiResponse.ok(
                "DRONE_STAGE_ADVANCED",
                "Drone mission advanced to the next stage",
                droneDeliveryQueryService.getForTechnician(orderId, userId));
    }

    @GetMapping("/api/orders/{orderId}/status")
    public ApiResponse<OrderStatusResponse> status(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        orderAccess.requireOwner(orderId, userId, roles);
        return ApiResponse.ok(orderService.status(orderId));
    }

    @GetMapping("/api/orders/my-orders")
    public ApiResponse<List<OrderResponse>> myOrders(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(orderService.listByUser(userId));
    }

    /// Đơn người khác gửi cho tôi (theo tài khoản người nhận hoặc SĐT tài khoản), mới nhất trước.
    /// PIN/QR chỉ có khi hàng đã sẵn sàng lấy. `limit` mặc định 50, tối đa 100.
    @GetMapping("/api/orders/incoming")
    public ApiResponse<List<ReceiverOrderResponse>> incoming(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(receiverOrderQueryService.incoming(userId, activeOnly, limit));
    }

    /// Đơn người khác ủy quyền cho tôi lấy hộ (khớp theo SĐT tài khoản), kèm mã mở ô khi sẵn sàng.
    @GetMapping("/api/orders/delegated-to-me")
    public ApiResponse<List<ReceiverOrderResponse>> delegatedToMe(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(receiverOrderQueryService.delegatedToMe(userId, activeOnly, limit));
    }

    /// Chi tiết một đơn gửi tới tôi hoặc ủy quyền cho tôi lấy hộ: liên hệ người gửi, kiện hàng,
    /// tủ/ô, mốc thời gian, hành trình drone. Đơn không thuộc hai danh sách trên của tôi ⇒ 404.
    /// Hai đường cùng một quy tắc quyền; `viewerRole` trong kết quả cho biết vai của người xem.
    @GetMapping({"/api/orders/incoming/{orderId}", "/api/orders/delegated-to-me/{orderId}"})
    public ApiResponse<ReceiverOrderDetailResponse> receiverOrderDetail(
            @PathVariable Long orderId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(receiverOrderQueryService.detail(userId, orderId));
    }

    @PostMapping("/api/orders/{orderId}/rate")
    public ApiResponse<OrderRatingResponse> rate(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderRatingRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok("ORDER_RATED", "Order rated", orderService.rate(orderId, request, userId));
    }

    @GetMapping("/api/orders/{orderId}/rating")
    public ApiResponse<OrderRatingResponse> rating(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        orderAccess.requireParticipant(orderId, userId, roles);
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
    public ApiResponse<List<OrderComplaintResponse>> complaints(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        orderAccess.requireParticipant(orderId, userId, roles);
        return ApiResponse.ok(orderService.complaints(orderId));
    }

    @GetMapping("/api/orders/my-complaints")
    public ApiResponse<List<OrderComplaintResponse>> myComplaints(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(orderService.myComplaints(userId));
    }

    @GetMapping("/api/orders/{orderId}/timeline")
    public ApiResponse<List<OrderTimelineEvent>> timeline(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        orderAccess.requireParticipant(orderId, userId, roles);
        return ApiResponse.ok(orderService.timeline(orderId));
    }

    @GetMapping("/internal/orders/{id}")
    public ApiResponse<OrderSummary> getInternal(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getSummary(id));
    }

    /// notification-service kiểm quyền đăng ký topic vị trí drone của đơn.
    @GetMapping("/internal/orders/{id}/participants")
    public ApiResponse<OrderParticipantsResponse> participants(@PathVariable Long id) {
        return ApiResponse.ok(orderService.participants(id));
    }

    @GetMapping("/api/admin/orders")
    public ApiResponse<List<OrderResponse>> adminOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(orderService.list(status, type, null));
    }

    @GetMapping("/api/admin/orders/{id}")
    public ApiResponse<OrderResponse> adminOrder(@PathVariable Long id) {
        return ApiResponse.ok(orderService.get(id));
    }

    /// Người thao tác ghi vào lịch sử đơn là admin trong JWT (X-User-Id), không lấy `staffId` từ body.
    @PutMapping("/api/admin/orders/{id}/status")
    public ApiResponse<OrderResponse> adminStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            @RequestHeader("X-User-Id") Long adminId) {
        return ApiResponse.ok(
                "ORDER_STATUS_UPDATED", "Order status updated", orderService.updateStatus(id, request.status(), adminId));
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
    public ApiResponse<PromotionView> createPromotion(
            @Valid @RequestBody PromotionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok("PROMOTION_CREATED", "Promotion created", orderService.createPromotion(request, userId));
    }

    /// lockerId > 0: mã của tủ đó; lockerId=0 hoặc scope=GLOBAL: chỉ mã toàn hệ thống;
    /// scope=LOCKER: mọi mã theo tủ. Mỗi mã kèm lockerCode/lockerName (null = toàn hệ thống).
    @GetMapping("/api/admin/promotions")
    public ApiResponse<List<PromotionView>> promotions(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long lockerId,
            @RequestParam(required = false) String scope) {
        return ApiResponse.ok(orderService.promotions(code, status, lockerId, scope));
    }

    @GetMapping("/api/admin/promotions/{promotionId}")
    public ApiResponse<PromotionView> promotion(@PathVariable Long promotionId) {
        return ApiResponse.ok(orderService.promotionView(promotionId));
    }

    @PutMapping("/api/admin/promotions/{promotionId}")
    public ApiResponse<PromotionView> updatePromotion(
            @PathVariable Long promotionId, @Valid @RequestBody PromotionRequest request) {
        return ApiResponse.ok("PROMOTION_UPDATED", "Promotion updated", orderService.updatePromotion(promotionId, request));
    }

    @DeleteMapping("/api/admin/promotions/{promotionId}")
    public ApiResponse<Void> deletePromotion(@PathVariable Long promotionId) {
        orderService.deletePromotion(promotionId);
        return ApiResponse.ok("PROMOTION_DELETED", "Promotion deleted");
    }

    // Ảnh khuyến mãi: client upload thẳng lên Cloudinary rồi gửi MediaUpload (ADR-0004).
    @PutMapping("/api/admin/promotions/{promotionId}/image")
    public ApiResponse<PromotionView> updatePromotionImage(
            @PathVariable Long promotionId,
            @RequestBody MediaUpload request,
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(
                "PROMOTION_IMAGE_UPDATED", "Promotion image updated",
                orderService.adminView(promotionImageService.updateImage(promotionId, request, userId)));
    }

    @DeleteMapping("/api/admin/promotions/{promotionId}/image")
    public ApiResponse<PromotionView> removePromotionImage(@PathVariable Long promotionId) {
        return ApiResponse.ok(
                "PROMOTION_IMAGE_REMOVED", "Promotion image removed",
                orderService.adminView(promotionImageService.removeImage(promotionId)));
    }

    @GetMapping("/api/admin/promotions/status/{status}")
    public ApiResponse<List<PromotionView>> promotionsByStatus(@PathVariable String status) {
        return ApiResponse.ok(orderService.promotionsByStatus(status));
    }

    @GetMapping("/api/admin/promotions/search")
    public ApiResponse<List<PromotionView>> searchPromotions(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(orderService.searchPromotions(keyword));
    }

    @GetMapping("/api/admin/promotions/validate/{code}")
    public ApiResponse<Map<String, Object>> adminValidatePromotion(@PathVariable String code) {
        return ApiResponse.ok(orderService.validatePromotion(code));
    }

    /// Có lockerId: mã toàn hệ thống + mã của đúng tủ đó; không có: tất cả. Mỗi mã kèm
    /// lockerId/lockerCode/lockerName (null = toàn hệ thống).
    @GetMapping("/api/promotions/active")
    public ApiResponse<List<PromotionView>> activePromotions(@RequestParam(required = false) Long lockerId) {
        return ApiResponse.ok(orderService.activePromotions(lockerId));
    }

    @GetMapping("/api/admin/promotions/active")
    public ApiResponse<List<PromotionView>> adminActivePromotions(@RequestParam(required = false) Long lockerId) {
        return activePromotions(lockerId);
    }

    // Validate mã cho client: lockerId để check scope theo tủ (mã theo tủ mà thiếu/khác tủ ⇒ valid:false);
    // orderTotal để check đơn tối thiểu; X-User-Id (gateway gắn khi có JWT) để check lượt dùng còn lại
    // của chính user đó. Trả {code, valid, reason?, promotion}.
    @GetMapping("/api/promotions/validate/{code}")
    public ApiResponse<Map<String, Object>> validatePromotion(
            @PathVariable String code,
            @RequestParam(required = false) Long lockerId,
            @RequestParam(required = false) java.math.BigDecimal orderTotal,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(orderService.validatePromotion(code, lockerId, userId, orderTotal, true));
    }

    // ---- Ví voucher của user (lưu mã -> dùng khi đặt đơn) ----

    @PostMapping("/api/promotions/{promotionId}/claim")
    public ApiResponse<Map<String, Object>> claimPromotion(
            @PathVariable Long promotionId, @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(
                "PROMOTION_CLAIMED", "Promotion saved to wallet",
                orderService.claimPromotion(promotionId, userId));
    }

    /// Có lockerId: chỉ mã toàn hệ thống + mã của đúng tủ đó. Mỗi mã kèm lockerCode/lockerName.
    @GetMapping("/api/promotions/vouchers/my")
    public ApiResponse<List<Map<String, Object>>> myVouchers(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long lockerId) {
        return ApiResponse.ok(orderService.myVouchers(userId, status, lockerId));
    }

    @GetMapping("/internal/orders/stores/{storeId}/ratings")
    public ApiResponse<List<Map<String, Object>>> storeRatings(@PathVariable Long storeId) {
        return ApiResponse.ok(storeRatingQueryService.storeRatings(storeId));
    }

    @PostMapping("/api/admin/scheduler/auto-cancel")
    public ApiResponse<Map<String, Object>> autoCancel() {
        Map<String, Object> result = orderService.autoCancelUnconfirmedOrders();
        com.huynqb.laundrylocker.order.service.OrderScheduler.refundPaidCanceled(result, droneRefundService);
        return ApiResponse.ok("SCHEDULER_AUTO_CANCEL_OK", "Auto cancel job completed", result);
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
