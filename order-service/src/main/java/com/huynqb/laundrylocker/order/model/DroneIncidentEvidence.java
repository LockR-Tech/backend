package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "drone_incident_evidence")
@Getter
@Setter
public class DroneIncidentEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "incident_id", nullable = false)
    private Long incidentId;
    @Column(nullable = false, length = 30)
    private String stage;
    @Column(name = "public_id", nullable = false, unique = true, length = 255)
    private String publicId;
    @Column(name = "secure_url", nullable = false, length = 1000)
    private String secureUrl;
    @Column(length = 500)
    private String caption;
    private Double latitude;
    private Double longitude;
    @Column(name = "gps_accuracy_m")
    private Double gpsAccuracyM;
    @Column(name = "captured_at")
    private Instant capturedAt;
    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

