package com.huynqb.laundrylocker.iot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Một dòng nhật ký điều khiển tủ: lệnh gửi từ admin web (`WEB`) hoặc việc tủ đã làm (`CABINET`) —
/// kích relay mở ô, chạy nắp/thanh trượt. `occurredAt` là giờ xảy ra thật (tủ mất mạng thì gửi muộn).
@Entity
@Table(name = "cabinet_control_logs")
@Getter
@Setter
public class CabinetControlLog {

    public static final String SOURCE_WEB = "WEB";
    public static final String SOURCE_CABINET = "CABINET";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(nullable = false, length = 30)
    private String action;

    private Integer axis;

    @Column(name = "box_id")
    private Long boxId;

    @Column(name = "box_number")
    private Integer boxNumber;

    private Double revolutions;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (occurredAt == null) {
            occurredAt = createdAt;
        }
    }
}
