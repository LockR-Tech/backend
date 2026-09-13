package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "lockers")
@Getter
@Setter
public class LockerUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id")
    private Long storeId;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    private String address;
    private Double latitude;
    private Double longitude;

    @Column(name = "landing_pad", nullable = false)
    private Boolean landingPad = false;

    @Column(name = "landing_marker_id", length = 50)
    private String landingMarkerId;

    /// Trang thai bao tri bai dap drone: OK / FAULT / MAINTENANCE.
    @Column(name = "landing_pad_status", nullable = false, length = 20)
    private String landingPadStatus = "OK";

    @Column(length = 2000)
    private String description;

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
