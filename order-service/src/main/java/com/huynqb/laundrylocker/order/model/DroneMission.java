package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "drone_missions")
@Getter
@Setter
public class DroneMission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "drone_unit_id")
    private Long droneUnitId;

    @Column(name = "drone_code", length = 80)
    private String droneCode;

    @Column(name = "source_locker_id")
    private Long sourceLockerId;

    @Column(name = "destination_locker_id", nullable = false)
    private Long destinationLockerId;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "assigned_by_user_id")
    private Long assignedByUserId;

    @Column(name = "last_accept_idempotency_key", length = 120)
    private String lastAcceptIdempotencyKey;

    @Column(name = "last_launch_idempotency_key", length = 120)
    private String lastLaunchIdempotencyKey;

    @Column(name = "ready_to_launch_at")
    private LocalDateTime readyToLaunchAt;

    @Column(name = "launching_at")
    private LocalDateTime launchingAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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
