package com.huynqb.laundrylocker.iot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Cấu hình phần cứng một tủ: `config` (bản đồ chân Pi dùng, JSON) và `wiring` (sơ đồ đấu dây để tra
/// cứu, JSON). Lần áp dụng xuống tủ gần nhất ghi ở `applied*`.
@Entity
@Table(name = "cabinet_hardware_configs")
@Getter
@Setter
public class CabinetHardwareConfig {

    @Id
    @Column(name = "locker_id")
    private Long lockerId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String config;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String wiring;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "applied_by")
    private Long appliedBy;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "apply_status", length = 20)
    private String applyStatus;

    @Column(name = "apply_message", length = 1000)
    private String applyMessage;
}
