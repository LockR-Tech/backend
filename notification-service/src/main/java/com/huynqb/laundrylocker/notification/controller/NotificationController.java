package com.huynqb.laundrylocker.notification.controller;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.notification.dto.*;
import com.huynqb.laundrylocker.notification.service.DeliveryNotificationService;
import com.huynqb.laundrylocker.notification.service.DronePositionService;
import com.huynqb.laundrylocker.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final DeliveryNotificationService deliveryNotificationService;
    private final DronePositionService dronePositionService;

    @PostMapping("/internal/notifications")
    public ApiResponse<NotificationResponse> createInternal(@Valid @RequestBody NotificationRequest request) {
        return ApiResponse.ok("NOTIFICATION_CREATED", "Notification created", notificationService.create(request));
    }

    /**
     * Gửi noti trạng thái giao hàng (drone) tới người nhận. Internal only — luồng
     * giao drone/order gọi service-to-service; gateway chặn /internal/** ra ngoài.
     */
    @PostMapping("/internal/notifications/delivery-status")
    public ApiResponse<NotificationResponse> deliveryStatus(
            @Valid @RequestBody DeliveryStatusNotificationRequest request) {
        return ApiResponse.ok(
                "DELIVERY_NOTIFICATION_SENT",
                "Delivery notification sent",
                deliveryNotificationService.notifyDeliveryStatus(request));
    }

    /**
     * Đẩy vị trí drone real-time cho NGƯỜI NHẬN theo dõi live map (Phase 2) qua
     * STOMP {@code /topic/deliveries/{orderId}/position}. Internal only — iot-service
     * gọi sau khi downsample telemetry; gateway chặn /internal/** ra ngoài.
     */
    @PostMapping("/internal/deliveries/{orderId}/position")
    public ApiResponse<Map<String, Object>> dronePosition(
            @PathVariable Long orderId, @Valid @RequestBody DronePositionRequest request) {
        return ApiResponse.ok(
                "DRONE_POSITION_BROADCAST",
                "Drone position broadcast",
                dronePositionService.broadcast(orderId, request));
    }

    @PostMapping("/internal/notifications/order-status")
    public ApiResponse<NotificationResponse> orderStatus(@Valid @RequestBody NotificationRequest request) {
        return ApiResponse.ok("ORDER_NOTIFICATION_CREATED", "Order notification created", notificationService.create(request));
    }

    @PostMapping("/internal/notifications/broadcast")
    public ApiResponse<List<NotificationResponse>> broadcastInternal(@Valid @RequestBody NotificationRequest request) {
        return ApiResponse.ok("BROADCAST_CREATED", "Broadcast notifications created", notificationService.broadcast(request));
    }

    @PostMapping("/internal/fcm-tokens")
    public ApiResponse<Void> saveFcmToken(@Valid @RequestBody FcmTokenRequest request) {
        notificationService.upsertFcmToken(request);
        return ApiResponse.ok("FCM_TOKEN_SAVED", "FCM token saved");
    }

    @DeleteMapping("/internal/fcm-tokens")
    public ApiResponse<Void> deleteFcmToken(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String token,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body != null) {
            userId = body.get("userId") == null ? userId : Long.valueOf(String.valueOf(body.get("userId")));
            token = body.get("token") == null ? token : String.valueOf(body.get("token"));
        }
        notificationService.deleteFcmToken(userId, token);
        return ApiResponse.ok("FCM_TOKEN_DELETED", "FCM token deleted");
    }

    @PostMapping("/api/notifications/fcm-tokens")
    public ApiResponse<Void> saveCurrentUserFcmToken(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody DeviceTokenRequest request) {
        notificationService.upsertFcmToken(new FcmTokenRequest(userId, request.token(), request.deviceType()));
        return ApiResponse.ok("FCM_TOKEN_SAVED", "FCM token saved");
    }

    @DeleteMapping("/api/notifications/fcm-tokens")
    public ApiResponse<Void> deleteCurrentUserFcmToken(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String token,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body != null && body.get("token") != null) {
            token = String.valueOf(body.get("token"));
        }
        notificationService.deleteFcmToken(userId, token);
        return ApiResponse.ok("FCM_TOKEN_DELETED", "FCM token deleted");
    }

    @GetMapping("/api/notifications/user/{userId}")
    public ApiResponse<List<NotificationResponse>> getByUser(@PathVariable Long userId) {
        return ApiResponse.ok(notificationService.getByUser(userId));
    }

    @GetMapping("/api/notifications")
    public ApiResponse<List<NotificationResponse>> getCurrentUser(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(notificationService.getByUser(userId));
    }

    @GetMapping("/api/notifications/all")
    public ApiResponse<List<NotificationResponse>> getAll(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(notificationService.getByUser(userId));
    }

    @GetMapping("/api/notifications/unread")
    public ApiResponse<List<NotificationResponse>> unread(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(notificationService.getUnreadByUser(userId));
    }

    @GetMapping("/api/notifications/unread/count")
    public ApiResponse<Long> unreadCount(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(notificationService.countUnread(userId));
    }

    @PatchMapping("/api/notifications/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(id));
    }

    @PutMapping("/api/notifications/{id}/read")
    public ApiResponse<NotificationResponse> markReadLegacy(@PathVariable Long id) {
        return markRead(id);
    }

    @PatchMapping("/api/notifications/read-all")
    public ApiResponse<Integer> markAllRead(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(notificationService.markAllRead(userId));
    }

    @PutMapping("/api/notifications/read-all")
    public ApiResponse<Integer> markAllReadLegacy(@RequestHeader("X-User-Id") Long userId) {
        return markAllRead(userId);
    }

    @PutMapping("/api/notifications/read-batch")
    public ApiResponse<List<NotificationResponse>> markBatchRead(@RequestBody Map<String, List<Long>> request) {
        return ApiResponse.ok(notificationService.markBatchRead(request.getOrDefault("ids", List.of())));
    }

    @DeleteMapping("/api/notifications/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return ApiResponse.ok("NOTIFICATION_DELETED", "Notification deleted");
    }

    @DeleteMapping("/api/notifications/all")
    public ApiResponse<Void> deleteAll(@RequestHeader("X-User-Id") Long userId) {
        notificationService.deleteAll(userId);
        return ApiResponse.ok("NOTIFICATIONS_DELETED", "Notifications deleted");
    }

    @GetMapping("/api/admin/notifications")
    public ApiResponse<List<NotificationResponse>> adminByUser(@RequestParam(required = false) Long userId) {
        return ApiResponse.ok(userId == null ? notificationService.all() : notificationService.getByUser(userId));
    }

    @PostMapping("/api/admin/notifications/send")
    public ApiResponse<NotificationResponse> adminSend(@Valid @RequestBody NotificationRequest request) {
        return ApiResponse.ok("NOTIFICATION_SENT", "Notification sent", notificationService.create(request));
    }

    @PostMapping("/api/admin/notifications/broadcast")
    public ApiResponse<List<NotificationResponse>> adminBroadcast(@Valid @RequestBody NotificationRequest request) {
        return ApiResponse.ok("BROADCAST_SENT", "Broadcast sent", notificationService.broadcast(request));
    }
}
