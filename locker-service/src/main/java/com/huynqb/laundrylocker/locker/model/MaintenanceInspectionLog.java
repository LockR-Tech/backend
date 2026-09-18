package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// L5: Nhật ký / Lịch sử một lượt kiểm tra định kỳ Kiosk hoặc Drone của KTV.
@Entity
@Table(name = "maintenance_inspection_logs")
@Getter
@Setter
public class MaintenanceInspectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "locker_id")
    private Long lockerId;

    @Column(name = "drone_unit_id")
    private Long droneUnitId;

    @Column(name = "technician_id")
    private Long technicianId;

    @Column(name = "technician_name")
    private String technicianName;

    @Column(nullable = false, length = 50)
    private String status = "PASSED";

    @Column(length = 2000)
    private String note;

    @Column(name = "photo_urls", columnDefinition = "TEXT")
    private String photoUrls;

    @Column(name = "checklist_results", columnDefinition = "TEXT")
    private String checklistResults;

    @Column(name = "created_report_id")
    private Long createdReportId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
