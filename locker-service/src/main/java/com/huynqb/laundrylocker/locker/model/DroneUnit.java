package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Một con drone vật lý gắn với một tủ (locker) làm trạm gốc.
@Entity
@EntityListeners({MaintenanceChangeListener.class, ActionAuditListener.class})
@Table(name = "drone_units")
@Getter
@Setter
public class DroneUnit implements AuditTracked {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 30)
    private String status = DroneStatus.IDLE;

    @Column(name = "battery_percent", nullable = false)
    private Integer batteryPercent = 100;

    @Column(name = "fault_reason", length = 255)
    private String faultReason;

    @Column(name = "assigned_technician_id")
    private Long assignedTechnicianId;

    @Column(name = "last_charged_at")
    private LocalDateTime lastChargedAt;

    /// false = drone da ngung hoat dong (decommission), an khoi danh sach van hanh.
    @Column(nullable = false)
    private Boolean active = true;

    /// Xoá mềm từ trang admin: còn nguyên trong DB kèm lý do, thời điểm và người xoá.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_reason", length = 500)
    private String deletedReason;

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

    /// Ảnh các trường lúc nạp từ DB — {@link ActionAuditListener} so để ghi nhật ký thao tác (F3-G16).
    @Transient
    private java.util.Map<String, String> auditBaseline;

    @Override
    public java.util.Map<String, String> auditFields() {
        java.util.Map<String, String> fields = new java.util.HashMap<>();
        put(fields,
                "status", s(status),
                "assignedTechnicianId", s(assignedTechnicianId),
                "lockerId", s(lockerId),
                "active", s(active),
                "deletedAt", s(deletedAt));
        return fields;
    }

    private static String s(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static void put(java.util.Map<String, String> fields, String... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            fields.put(pairs[i], pairs[i + 1]);
        }
    }
}
