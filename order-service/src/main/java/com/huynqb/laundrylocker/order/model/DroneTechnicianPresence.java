package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// KTV drone có đang trong ca không và vị trí gần nhất app của họ gửi lên.
@Entity
@Table(name = "drone_technician_presence")
@Getter
@Setter
public class DroneTechnicianPresence {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "on_duty", nullable = false)
    private boolean onDuty;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(name = "location_at")
    private LocalDateTime locationAt;

    @Column(name = "duty_changed_at")
    private LocalDateTime dutyChangedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
