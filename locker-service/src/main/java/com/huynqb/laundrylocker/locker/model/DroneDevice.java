package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Bộ điều khiển drone (Raspberry Pi Companion + Pixhawk Autopilot + Camera)
/// Tương tự GatewayDevice của Kiosk tủ.
@Entity
@Table(name = "drone_devices")
@Getter
@Setter
public class DroneDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /// MAC wlan0/eth0 của Pi (duy nhất, viết HOA có dấu :)
    @Column(name = "mac_address", nullable = false, unique = true, length = 17)
    private String macAddress;

    /// Drone được gán vào (1 Pi gán vào 1 Drone tại 1 thời điểm)
    @Column(name = "drone_id", unique = true)
    private Long droneId;

    /// Mã drone (DRONE-S550-07)
    @Column(name = "drone_code", length = 50)
    private String droneCode;

    /// Địa chỉ IP mạng của Pi
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion = "v1.0.0";

    /// MAVLink link state: CONNECTED, HEARTBEAT_LOST, DISCONNECTED
    @Column(name = "mavlink_status", length = 30)
    private String mavlinkStatus = "DISCONNECTED";

    /// Chế độ bay: LOITER, AUTO, GUIDED, STABILIZE, RTL...
    @Column(name = "flight_mode", length = 30)
    private String flightMode = "UNKNOWN";

    /// Động cơ đã Arm chưa
    @Column(name = "armed")
    private Boolean armed = false;

    /// ON_GROUND, IN_AIR, TAKEOFF, LANDING
    @Column(name = "landed_state", length = 30)
    private String landedState = "ON_GROUND";

    @Column(name = "gps_fix_type")
    private Integer gpsFixType = 0;

    @Column(name = "gps_satellites")
    private Integer gpsSatellites = 0;

    @Column(name = "battery_percent")
    private Integer batteryPercent = 100;

    @Column(name = "voltage_v", precision = 5, scale = 2)
    private BigDecimal voltageV;

    @Column(name = "current_a", precision = 5, scale = 2)
    private BigDecimal currentA;

    /// LIVE, OFFLINE
    @Column(name = "camera_status", length = 30)
    private String cameraStatus = "OFFLINE";

    /// URL stream camera riêng của drone này (MJPEG / HLS / WebRTC)
    @Column(name = "camera_stream_url")
    private String cameraStreamUrl;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

