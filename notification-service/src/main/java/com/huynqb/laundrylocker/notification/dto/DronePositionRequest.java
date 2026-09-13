package com.huynqb.laundrylocker.notification.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Snapshot vị trí drone (đã downsample) để đẩy real-time cho NGƯỜI NHẬN qua
 * STOMP {@code /topic/deliveries/{orderId}/position} — Phase 2 live map.
 *
 * <p>Gọi nội bộ (service-to-service) bởi iot-service/ground-station sau khi
 * downsample telemetry đầy đủ. KHÔNG expose qua gateway. {@code orderId} lấy từ
 * path. Cố ý gọn — KHÔNG đẩy full MAVLink/altitude/attitude xuống app user.
 *
 * @param status     mốc giao: dispatched/approaching/arrived/delivered/delayed/failed
 * @param lat        vĩ độ (bắt buộc)
 * @param lng        kinh độ (bắt buộc)
 * @param heading    hướng mũi drone, độ (0 = Bắc, thuận kim đồng hồ)
 * @param etaMinutes phút ước tính tới nơi (tuỳ chọn)
 * @param speed      tốc độ m/s (tuỳ chọn — view người nhận không bắt buộc)
 * @param battery    % pin (tuỳ chọn)
 * @param ts         epoch millis thời điểm đo (tuỳ chọn; thiếu → server điền thời điểm nhận)
 */
public record DronePositionRequest(
        String status,
        @NotNull Double lat,
        @NotNull Double lng,
        Double heading,
        Integer etaMinutes,
        Double speed,
        Integer battery,
        Long ts) {
}
