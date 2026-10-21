package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Nhật ký IoT Drone: theo dõi trạng thái các thành phần phần cứng (Pi Controller, Pixhawk, Móc treo hàng, Camera thị giác)
/// Bỏ cột payload và vị trí riêng biệt; toàn bộ chi tiết được ghi đầy đủ vào trường message (hỗ trợ xuống dòng)
@Entity
@Table(name = "drone_iot_logs", schema = "locker_schema")
@Getter
@Setter
public class DroneIotLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drone_id", nullable = false)
    private Long droneId;

    @Column(name = "drone_code", nullable = false, length = 50)
    private String droneCode;

    /// PI_CONTROLLER, PIXHAWK, CARGO_HOOK, VISION_CAMERA, BATTERY_POWER, TELEMETRY
    @Column(name = "component", nullable = false, length = 50)
    private String component;

    /// POWER_CONNECTED, POWER_DISCONNECTED, PIXHAWK_CONNECTED, PIXHAWK_DISCONNECTED, DETECT_TARGET, HOOK_RELEASE, HOOK_ATTACH, HOOK_JAMMED, MANUAL_OVERRIDE, ARMED, DISARMED...
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /// ONLINE, OFFLINE, SUCCESS, FAILED, CONNECTED, DISCONNECTED, RELEASED, LOCKED, DETECTED, TIMEOUT, JAMMED, WARNING
    @Column(name = "result", nullable = false, length = 30)
    private String result;

    /// SYSTEM, ORDER, USER, TECHNICIAN, AUTOPILOT
    @Column(name = "actor_type", nullable = false, length = 30)
    private String actorType;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
