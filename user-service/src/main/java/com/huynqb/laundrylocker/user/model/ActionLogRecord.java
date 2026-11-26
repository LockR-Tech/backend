package com.huynqb.laundrylocker.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Một dòng nhật ký thao tác (F3-G16) — xem migration V10.
@Entity
@Table(name = "action_logs")
@Getter
@Setter
public class ActionLogRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", unique = true, length = 64)
    private String eventId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(length = 60)
    private String source;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_roles")
    private String actorRoles;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "target_type", length = 40)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_label")
    private String targetLabel;

    @Column(name = "locker_id")
    private Long lockerId;

    @Column(name = "from_value", length = 500)
    private String fromValue;

    @Column(name = "to_value", length = 500)
    private String toValue;

    @Column(length = 2000)
    private String note;
}
