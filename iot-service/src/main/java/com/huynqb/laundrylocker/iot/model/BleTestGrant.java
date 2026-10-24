package com.huynqb.laundrylocker.iot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Quyền thử mở ô qua Bluetooth của một tài khoản trên một tủ — admin cấp ở trang chi tiết tủ (F3-G11).
@Entity
@Table(name = "ble_test_grants")
@Getter
@Setter
public class BleTestGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /// Admin đã cấp quyền.
    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
