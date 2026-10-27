package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Trạm bay (tủ có bãi đáp) đang bị admin tạm dừng. Có dòng = đang dừng, trừ khi đã quá `resumeAt`.
@Entity
@Table(name = "drone_station_suspensions", schema = "order_schema")
@Getter
@Setter
public class DroneStationSuspension {

    @Id
    @Column(name = "locker_id")
    private Long lockerId;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(length = 500)
    private String note;

    @Column(name = "suspended_by_user_id")
    private Long suspendedByUserId;

    @Column(name = "suspended_at", nullable = false)
    private LocalDateTime suspendedAt;

    /// Tự mở lại lúc này; null = dừng tới khi admin mở lại.
    @Column(name = "resume_at")
    private LocalDateTime resumeAt;

    public boolean activeAt(LocalDateTime now) {
        return resumeAt == null || resumeAt.isAfter(now);
    }
}
