package com.huynqb.laundrylocker.notification.service;

import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.notification.dto.DeliveryStatusNotificationRequest;
import com.huynqb.laundrylocker.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Gửi noti theo 6 mốc trạng thái của chuyến giao hàng (drone) tới người nhận.
 *
 * <p>Nhắm theo {@code receiverUserId} (tái dùng {@code fcm_tokens} theo user). Mỗi
 * message gửi kèm phần data {@code {orderId, status, eta, message}} để mobile chọn
 * icon/màu theo status và deep-link sang chi tiết đơn theo orderId; đồng thời giữ
 * {@code type=ORDER_STATUS_CHANGED} + {@code referenceId=orderId} để client tự
 * refresh trạng thái đơn qua cơ chế sẵn có. Noti cũng được lưu vào lịch sử.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryNotificationService {

    private final NotificationService notificationService;

    /**
     * 6 mốc trạng thái + tiêu đề/nội dung tiếng Việt mặc định.
     */
    public enum DeliveryStatus {
        DISPATCHED("dispatched", "Drone đang giao hàng", "Drone đang trên đường giao hàng của bạn"),
        APPROACHING("approaching", "Drone sắp đến", "Drone sắp đến nơi, vui lòng chuẩn bị ra nhận"),
        ARRIVED("arrived", "Drone đã đến nơi", "Drone đã đến nơi, vui lòng ra nhận hàng"),
        DELIVERED("delivered", "Giao hàng thành công", "Đã giao hàng thành công. Cảm ơn bạn!"),
        DELAYED("delayed", "Đơn hàng bị chậm", "Đơn của bạn đang bị chậm"),
        FAILED("failed", "Giao hàng không thành công", "Giao không thành công, drone đang quay về"),
        UNKNOWN("unknown", "Cập nhật đơn hàng", "Đơn hàng của bạn có cập nhật mới");

        private final String code;
        private final String title;
        private final String body;

        DeliveryStatus(String code, String title, String body) {
            this.code = code;
            this.title = title;
            this.body = body;
        }

        public String code() {
            return code;
        }

        public String title() {
            return title;
        }

        /**
         * Nội dung mặc định; mốc delayed chèn thêm eta nếu có.
         */
        public String body(String eta) {
            if (this == DELAYED && StringUtils.hasText(eta)) {
                return "Đơn của bạn đang bị chậm, dự kiến tới " + eta;
            }
            return body;
        }

        static DeliveryStatus fromCode(String raw) {
            if (raw != null) {
                for (DeliveryStatus s : values()) {
                    if (s.code.equalsIgnoreCase(raw.trim())) {
                        return s;
                    }
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * Gửi noti trạng thái giao hàng (gọi từ internal endpoint).
     */
    public NotificationResponse notifyDeliveryStatus(DeliveryStatusNotificationRequest request) {
        DeliveryStatus status = DeliveryStatus.fromCode(request.status());
        String title = status.title();
        String body = status.body(request.eta());

        Map<String, String> data = new HashMap<>();
        data.put("orderId", String.valueOf(request.orderId()));
        data.put("status", status.code());
        data.put("message", body);
        if (StringUtils.hasText(request.eta())) {
            data.put("eta", request.eta());
        }

        // type=ORDER_STATUS_CHANGED + referenceId=orderId để client tự refresh đơn;
        // referenceType=DELIVERY để phân biệt nguồn.
        NotificationRequest notification =
                new NotificationRequest(
                        request.receiverUserId(), title, body, "ORDER_STATUS_CHANGED", request.orderId(), "DELIVERY");

        log.info("Gửi noti giao hàng status={} order={} user={}", status.code(), request.orderId(), request.receiverUserId());
        return notificationService.create(notification, data);
    }

    /**
     * Xử lý khi nhận event {@code delivery.status.changed} từ RabbitMQ (luồng giao
     * drone tương lai sẽ bắn event này). Payload kỳ vọng: orderId, userId (người
     * nhận), status, eta.
     */
    public void notifyFromEvent(DomainEvent event) {
        Map<String, Object> payload = event.payload();
        Long orderId = asLong(payload.get("orderId"));
        Long userId = asLong(payload.get("userId"));
        if (orderId == null || userId == null) {
            log.debug("Bỏ qua delivery.status.changed thiếu orderId/userId");
            return;
        }
        String status = String.valueOf(payload.getOrDefault("status", "unknown"));
        String eta = payload.get("eta") == null ? null : String.valueOf(payload.get("eta"));
        notifyDeliveryStatus(new DeliveryStatusNotificationRequest(orderId, userId, status, eta));
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
