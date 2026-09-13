package com.huynqb.laundrylocker.iot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// GAP 2: latest physical/hardware state of a box as reported by the cabinet
/// (door open/closed sensor). Deliberately separate from locker-service's
/// order-driven `LockerBox.status` — this is hardware truth, never used to
/// overwrite the logical order state, only to let ops spot mismatches.
@Entity
@Table(name = "box_hardware_status")
@Getter
@Setter
public class BoxHardwareStatus {

    /// Natural key — one row per box (upserted on each report).
    @Id
    @Column(name = "box_id")
    private Long boxId;

    @Column(name = "locker_id")
    private Long lockerId;

    @Column(name = "hw_state", nullable = false, length = 30)
    private String hwState = "UNKNOWN";

    @Column(name = "last_reported_at", nullable = false)
    private LocalDateTime lastReportedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (lastReportedAt == null) {
            lastReportedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
