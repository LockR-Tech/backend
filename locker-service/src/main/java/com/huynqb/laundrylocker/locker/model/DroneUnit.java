package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Một con drone vật lý gắn với một tủ (locker) làm trạm gốc.
@Entity
@Table(name = "drone_units")
@Getter
@Setter
public class DroneUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 30)
    private String status = DroneStatus.IDLE;

    @Column(name = "battery_percent", nullable = false)
    private Integer batteryPercent = 100;

    @Column(name = "fault_reason", length = 255)
    private String faultReason;

    @Column(name = "assigned_technician_id")
    private Long assignedTechnicianId;

    @Column(name = "last_charged_at")
    private LocalDateTime lastChargedAt;

    /// false = drone da ngung hoat dong (decommission), an khoi danh sach van hanh.
    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
