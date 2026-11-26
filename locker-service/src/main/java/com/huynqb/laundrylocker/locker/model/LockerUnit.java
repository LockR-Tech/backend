package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@SQLRestriction("deleted_at IS NULL")
@EntityListeners({MaintenanceChangeListener.class, ActionAuditListener.class})
@Table(name = "lockers")
@Getter
@Setter
public class LockerUnit implements AuditTracked {

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
    private Boolean landingPad = true;

    @Column(name = "landing_marker_id", length = 50)
    private String landingMarkerId;

    /// Trang thai bao tri bai dap drone: OK / FAULT / MAINTENANCE.
    @Column(name = "landing_pad_status", nullable = false, length = 20)
    private String landingPadStatus = "OK";

    @Column(length = 2000)
    private String description;

    /// KTV tủ phụ trách — phiếu sự cố của tủ được báo cho người này. NULL = báo mọi KTV tủ.
    @Column(name = "assigned_technician_id")
    private Long assignedTechnicianId;

    /// Ai đưa tủ vào MAINTENANCE: ADMIN (bật tay) hoặc TICKET (phiếu chặn tủ).
    /// Đóng phiếu chỉ tự trả tủ về ACTIVE khi nguồn là TICKET.
    @Column(name = "maintenance_source", length = 20)
    private String maintenanceSource;

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
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
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
                "maintenanceSource", s(maintenanceSource),
                "assignedTechnicianId", s(assignedTechnicianId),
                "landingPadStatus", s(landingPadStatus),
                "landingPad", s(landingPad),
                "code", s(code),
                "name", s(name),
                "address", s(address),
                "latitude", s(latitude),
                "longitude", s(longitude),
                "storeId", s(storeId),
                "description", s(description),
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
