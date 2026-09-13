package com.huynqb.laundrylocker.iot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// One row per box tracking consecutive failed PIN/QR verifications, for
/// brute-force lockout. `boxId` is the primary key (one counter per box).
@Entity
@Table(name = "access_attempts")
@Getter
@Setter
public class AccessAttempt {

    @Id
    @Column(name = "box_id")
    private Long boxId;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
