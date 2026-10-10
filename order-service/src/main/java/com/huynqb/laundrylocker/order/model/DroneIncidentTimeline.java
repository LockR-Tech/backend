package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "drone_incident_timeline")
@Getter
@Setter
public class DroneIncidentTimeline {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "incident_id", nullable = false)
    private Long incidentId;
    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;
    @Column(name = "from_status", length = 50)
    private String fromStatus;
    @Column(name = "to_status", length = 50)
    private String toStatus;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Column(length = 2000)
    private String note;
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

