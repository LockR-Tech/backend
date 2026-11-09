package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Kết quả một bước nghiệm thu. `verifiedBy` = SYSTEM (máy tự xác nhận) hoặc TECHNICIAN; `detail` là JSON
/// (vd. kết quả mở thử từng ô).
@Entity
@Table(name = "locker_installation_checks")
@Getter
@Setter
public class LockerInstallationCheck {

    public static final String PENDING = "PENDING";
    public static final String PASSED = "PASSED";
    public static final String FAILED = "FAILED";
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "installation_id", nullable = false)
    private Long installationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InstallationCheckItem item;

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "verified_by", length = 20)
    private String verifiedBy;

    @Column(length = 500)
    private String note;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "checked_by")
    private Long checkedBy;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;
}
