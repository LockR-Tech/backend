package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "technician_presences")
@Getter
@Setter
public class TechnicianPresence {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "technician_type", nullable = false, length = 50)
    private String technicianType;

    @Column(name = "on_duty", nullable = false)
    private Boolean onDuty = false;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "accuracy_m")
    private Double accuracyM;

    @Column(name = "heading_deg")
    private Double headingDeg;

    @Column(name = "speed_ms")
    private Double speedMs;

    @Column(name = "altitude_m")
    private Double altitudeM;

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(name = "shift_started_at")
    private LocalDateTime shiftStartedAt;

    @Column(name = "shift_ended_at")
    private LocalDateTime shiftEndedAt;

    @Column(name = "last_located_at")
    private LocalDateTime lastLocatedAt;

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

