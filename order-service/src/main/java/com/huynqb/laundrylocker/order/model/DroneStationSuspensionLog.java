package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Nhật ký dừng / mở lại một trạm bay.
@Entity
@Table(name = "drone_station_suspension_logs", schema = "order_schema")
@Getter
@Setter
public class DroneStationSuspensionLog {

    public static final String SUSPEND = "SUSPEND";
    public static final String RESUME = "RESUME";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(length = 30)
    private String reason;

    @Column(length = 500)
    private String note;

    @Column(name = "resume_at")
    private LocalDateTime resumeAt;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
