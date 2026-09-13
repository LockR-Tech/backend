package com.huynqb.laundrylocker.iot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Audit trail for every box-open attempt: who (actor), with what credential
/// (PIN/QR vs MASTER override), and the outcome. Written by both the regular
/// customer unlock path and the maintenance force-unlock path.
@Entity
@Table(name = "box_access_logs")
@Getter
@Setter
public class BoxAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "box_id", nullable = false)
    private Long boxId;

    @Column(name = "locker_id")
    private Long lockerId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "credential_type", nullable = false, length = 20)
    private String credentialType;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(length = 255)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
