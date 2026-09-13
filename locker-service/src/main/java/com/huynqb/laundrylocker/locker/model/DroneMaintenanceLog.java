package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Một dòng nhật ký xử lý của kỹ thuật viên cho một con drone.
@Entity
@Table(name = "drone_maintenance_logs")
@Getter
@Setter
public class DroneMaintenanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drone_unit_id", nullable = false)
    private Long droneUnitId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(nullable = false, length = 2000)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
