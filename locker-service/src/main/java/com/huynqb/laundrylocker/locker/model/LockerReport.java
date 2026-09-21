package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "locker_reports")
@Getter
@Setter
public class LockerReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @Column(name = "box_id")
    private Long boxId;

    /// Khi phieu su co gan voi 1 drone vat ly (box_id se NULL).
    @Column(name = "drone_unit_id")
    private Long droneUnitId;

    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    @Column(name = "sla_extended_hours")
    private Integer slaExtendedHours = 0;

    @Column(name = "sla_extension_reason", length = 1000)
    private String slaExtensionReason;

    /// KTV được báo khi phiếu còn OPEN (KTV phụ trách tủ). NULL = đã báo mọi KTV tủ.
    @Column(name = "routed_to_user_id")
    private Long routedToUserId;

    /// Lịch kiểm tra định kỳ sinh ra phiếu này (lần kiểm tra KHÔNG ĐẠT).
    @Column(name = "schedule_id")
    private Long scheduleId;

    /// Xem {@link ReportCategory}.
    @Column(length = 20)
    private String category = ReportCategory.LOCKER;

    /// Phiếu đưa cả tủ vào MAINTENANCE; đóng phiếu cuối cùng loại này thì tủ về ACTIVE.
    @Column(name = "blocks_locker", nullable = false)
    private Boolean blocksLocker = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (slaDueAt == null) {
            slaDueAt = createdAt.plusHours(4);
        }
        if (slaExtendedHours == null) {
            slaExtendedHours = 0;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
