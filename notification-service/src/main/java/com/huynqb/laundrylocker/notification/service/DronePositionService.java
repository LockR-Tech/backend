package com.huynqb.laundrylocker.notification.service;

import com.huynqb.laundrylocker.notification.dto.DronePositionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Đẩy snapshot vị trí drone real-time cho NGƯỜI NHẬN theo dõi trên live map
 * (Phase 2) qua STOMP topic {@code /topic/deliveries/{orderId}/position}.
 *
 * <p>Chỉ forward snapshot GỌN (đã downsample bởi iot-service) tới WebSocket —
 * KHÔNG lưu DB, KHÔNG gửi FCM (khác hẳn {@link DeliveryNotificationService} lo
 * push 6 mốc trạng thái). TUYỆT ĐỐI không đẩy full MAVLink telemetry xuống app.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DronePositionService {

    private final WebSocketNotificationService webSocketNotificationService;

    public static String destination(Long orderId) {
        return "/topic/deliveries/" + orderId + "/position";
    }

    /**
     * Broadcast 1 snapshot vị trí tới topic của {@code orderId}. Trả lại payload
     * đã chuẩn hoá (kèm ts được điền nếu thiếu) để controller phản hồi/log.
     */
    public Map<String, Object> broadcast(Long orderId, DronePositionRequest req) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("status", req.status());
        payload.put("lat", req.lat());
        payload.put("lng", req.lng());
        payload.put("heading", req.heading() == null ? 0.0 : req.heading());
        payload.put("etaMinutes", req.etaMinutes());
        payload.put("speed", req.speed());
        payload.put("battery", req.battery());
        payload.put("ts", req.ts() == null ? System.currentTimeMillis() : req.ts());

        webSocketNotificationService.sendToDestination(destination(orderId), payload);
        log.debug("Broadcast drone position for order {} -> {}", orderId, payload);
        return payload;
    }
}
