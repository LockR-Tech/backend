package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// L5: lịch bảo trì phòng ngừa (kiểm tra định kỳ) cho một tủ.
@Entity
@Table(name = "maintenance_schedules")
@Getter
@Setter
public class MaintenanceSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /// Lich nham vao 1 tu; NULL khi lich danh cho 1 drone (xem droneUnitId).
    @Column(name = "locker_id")
    private Long lockerId;

    /// Lich nham vao 1 drone vat ly; NULL khi lich danh cho 1 tu.
    @Column(name = "drone_unit_id")
    private Long droneUnitId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "interval_days", nullable = false)
    private Integer intervalDays;

    @Column(name = "last_done_at")
    private LocalDateTime lastDoneAt;

    @Column(name = "next_due_at", nullable = false)
    private LocalDateTime nextDueAt;

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
