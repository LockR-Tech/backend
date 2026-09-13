package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "locker_boxes")
@Getter
@Setter
public class LockerBox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Column(name = "box_number", nullable = false)
    private Integer boxNumber;

    @Column(length = 30)
    private String size = "MEDIUM";

    /**
     * Loại ô — xem {@link CellType} để biết các giá trị hợp lệ và ngữ nghĩa từng loại.
     * Mặc định {@link CellType#STANDARD} (đa dịch vụ).
     */
    @Column(name = "cell_type", nullable = false, length = 30)
    private String cellType = CellType.STANDARD;

    @Column(name = "row_index")
    private Integer rowIndex;

    @Column(name = "col_index")
    private Integer colIndex;

    @Column(name = "fault_reason", length = 500)
    private String faultReason;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 30)
    private String status = "AVAILABLE";

    /// Backstop expiry for a RESERVED hold; see LockerScheduler.sweepExpiredReservations.
    @Column(name = "reserved_until")
    private LocalDateTime reservedUntil;

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
