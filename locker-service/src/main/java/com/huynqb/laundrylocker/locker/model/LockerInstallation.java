package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Hồ sơ lắp đặt & nghiệm thu một tủ (F3-G12): lịch, KTV lắp đặt, thông tin hiện trường / phần cứng, kết quả duyệt.
@Entity
@Table(name = "locker_installations")
@Getter
@Setter
public class LockerInstallation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallationStatus status;

    @Column(name = "previous_locker_status", length = 30)
    private String previousLockerStatus;

    @Column(name = "technician_id", nullable = false)
    private Long technicianId;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "site_contact_name", length = 100)
    private String siteContactName;

    @Column(name = "site_contact_phone", length = 20)
    private String siteContactPhone;

    @Column(name = "power_source", length = 100)
    private String powerSource;

    @Column(name = "network_type", length = 20)
    private String networkType;

    @Column(name = "network_note", length = 255)
    private String networkNote;

    @Column(name = "cabinet_model", length = 100)
    private String cabinetModel;

    @Column(name = "cabinet_serial", length = 100)
    private String cabinetSerial;

    @Column(length = 1000)
    private String notes;

    @Column(name = "return_reason", length = 500)
    private String returnReason;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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
