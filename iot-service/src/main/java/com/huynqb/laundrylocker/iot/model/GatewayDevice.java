package com.huynqb.laundrylocker.iot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Bộ điều khiển tủ (Raspberry Pi) — tự báo qua `iot/{mac}/discovery/result`, admin gán
/// vào một tủ (ADR-0008, docs/01-overview/mqtt-contract.md § 3).
@Entity
@Table(name = "gateway_devices")
@Getter
@Setter
public class GatewayDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /// MAC `wlan0` của Pi, chữ HOA có dấu `:` — cũng là username trên broker riêng.
    @Column(name = "mac_address", nullable = false, unique = true, length = 17)
    private String macAddress;

    /// `gpio`, `rs485` hoặc `simulation` — Pi tự báo.
    @Column(length = 30)
    private String hardware;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Column(name = "slave_id", nullable = false)
    private Integer slaveId = 1;

    /// Số ô phần cứng Pi điều khiển được; null = chưa biết.
    @Column(name = "available_slots")
    private Integer availableSlots;

    /// Tủ Pi tự báo đang phục vụ (đã setup, hoặc `LOCKER_ID` trong `.env` của Pi).
    @Column(name = "reported_locker_id")
    private Long reportedLockerId;

    /// Tủ admin gán cho Pi.
    @Column(name = "locker_id", unique = true)
    private Long lockerId;

    /// NONE · PENDING · RUNNING · COMPLETED · PARTIAL · FAILED · CLEARED
    @Column(name = "setup_status", nullable = false, length = 20)
    private String setupStatus = "NONE";

    @Column(name = "setup_command_id", length = 64)
    private String setupCommandId;

    /// "3/7" khi Pi đang mở thử từng ô.
    @Column(name = "setup_progress", length = 20)
    private String setupProgress;

    /// Nguyên văn `iot/{mac}/setup/result` gần nhất (JSON).
    @Column(name = "setup_result", columnDefinition = "TEXT")
    private String setupResult;

    @Column(name = "setup_requested_at")
    private LocalDateTime setupRequestedAt;

    @Column(name = "setup_finished_at")
    private LocalDateTime setupFinishedAt;

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
