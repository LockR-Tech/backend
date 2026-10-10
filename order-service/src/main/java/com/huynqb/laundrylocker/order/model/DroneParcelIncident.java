package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "drone_parcel_incidents")
@Getter
@Setter
public class DroneParcelIncident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_code", nullable = false, unique = true, length = 64)
    private String incidentCode;
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;
    @Column(name = "mission_id", nullable = false, unique = true)
    private Long missionId;
    @Column(name = "drone_unit_id", nullable = false)
    private Long droneUnitId;
    @Column(name = "drone_code", nullable = false, length = 80)
    private String droneCode;
    @Column(name = "reported_by_user_id", nullable = false)
    private Long reportedByUserId;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String idempotencyKey;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(nullable = false, length = 50)
    private String status;
    @Column(name = "parcel_status", nullable = false, length = 40)
    private String parcelStatus;
    @Column(name = "recovery_status", nullable = false, length = 40)
    private String recoveryStatus;
    @Column(name = "inspection_status", nullable = false, length = 40)
    private String inspectionStatus;
    @Column(name = "redelivery_status", nullable = false, length = 40)
    private String redeliveryStatus;
    @Column(name = "compensation_status", nullable = false, length = 40)
    private String compensationStatus;
    @Column(name = "return_flight_status", nullable = false, length = 50)
    private String returnFlightStatus;
    @Column(name = "drop_latitude")
    private Double dropLatitude;
    @Column(name = "drop_longitude")
    private Double dropLongitude;
    @Column(name = "gps_accuracy_m")
    private Double gpsAccuracyM;
    @Column(name = "gps_source", nullable = false, length = 30)
    private String gpsSource;
    @Column(name = "telemetry_observed_at")
    private Instant telemetryObservedAt;
    @Column(name = "telemetry_stale", nullable = false)
    private boolean telemetryStale;
    @Column(name = "telemetry_json", columnDefinition = "TEXT")
    private String telemetryJson;
    @Column(name = "camera_status", nullable = false, length = 30)
    private String cameraStatus;
    @Column(name = "camera_snapshot_url", length = 1000)
    private String cameraSnapshotUrl;
    @Column(name = "inspection_report_id")
    private Long inspectionReportId;
    @Column(name = "recovery_assigned_to_user_id")
    private Long recoveryAssignedToUserId;
    @Column(name = "recovery_locker_id")
    private Long recoveryLockerId;
    @Column(name = "recovery_started_at")
    private LocalDateTime recoveryStartedAt;
    @Column(name = "recovery_submitted_at")
    private LocalDateTime recoverySubmittedAt;
    @Column(name = "recovery_verified_at")
    private LocalDateTime recoveryVerifiedAt;
    @Column(name = "recovery_outcome", length = 30)
    private String recoveryOutcome;
    @Column(name = "parcel_condition", length = 30)
    private String parcelCondition;
    @Column(name = "recovery_note", length = 2000)
    private String recoveryNote;
    @Column(name = "recovered_latitude")
    private Double recoveredLatitude;
    @Column(name = "recovered_longitude")
    private Double recoveredLongitude;
    @Column(name = "recovered_gps_accuracy_m")
    private Double recoveredGpsAccuracyM;
    @Column(name = "returned_to_hub_at")
    private LocalDateTime returnedToHubAt;
    @Column(name = "returned_to_hub_by_user_id")
    private Long returnedToHubByUserId;
    @Column(name = "redelivery_order_id")
    private Long redeliveryOrderId;
    @Column(name = "compensation_reference", length = 160)
    private String compensationReference;
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Version
    private Long version;

    @PrePersist
    void createTimestamps() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}

