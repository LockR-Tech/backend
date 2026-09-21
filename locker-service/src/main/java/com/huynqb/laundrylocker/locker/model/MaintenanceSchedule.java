package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// L5: lịch bảo trì phòng ngừa (kiểm tra định kỳ) cho một tủ.
@Entity
@Table(name = "maintenance_schedules")
@Getter
@Setter
public class MaintenanceSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /// Lich nham vao 1 tu; NULL khi lich danh cho 1 drone (xem droneUnitId).
    @Column(name = "locker_id")
    private Long lockerId;

    /// Lich nham vao 1 drone vat ly; NULL khi lich danh cho 1 tu.
    @Column(name = "drone_unit_id")
    private Long droneUnitId;

    @Column(name = "assigned_technician_id")
    private Long assignedTechnicianId;

    @Column(length = 20)
    private String priority = "NORMAL";

    @Column(length = 2000)
    private String description;

    @Column(name = "location_note", length = 500)
    private String locationNote;

    @Column(name = "scheduled_time_slot", length = 100)
    private String scheduledTimeSlot;

    @Column(columnDefinition = "TEXT")
    private String checklist;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "interval_days", nullable = false)
    private Integer intervalDays;

    @Column(name = "last_done_at")
    private LocalDateTime lastDoneAt;

    @Column(name = "next_due_at", nullable = false)
    private LocalDateTime nextDueAt;

    @Column(nullable = false)
    private Boolean active = true;

    /// Phiếu sinh từ lần kiểm tra KHÔNG ĐẠT; hạn kế tiếp chỉ dời khi phiếu này đóng.
    @Column(name = "pending_report_id")
    private Long pendingReportId;

    /// PASSED / FAILED của lần kiểm tra gần nhất.
    @Column(name = "last_result", length = 20)
    private String lastResult;

    /// Đã nhắc KTV cho kỳ hạn hiện tại — mỗi kỳ chỉ nhắc một lần.
    @Column(name = "last_due_notified_at")
    private LocalDateTime lastDueNotifiedAt;

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
}
