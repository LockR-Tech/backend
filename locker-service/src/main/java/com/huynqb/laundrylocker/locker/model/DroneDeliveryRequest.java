package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Yêu cầu giao hàng bằng drone do KHÁCH tạo; đội bay (MAINTENANCE) điều phối.
/// PENDING -> DISPATCHED -> DELIVERED; khách huỷ được khi còn PENDING.
@Entity
@Table(name = "drone_delivery_requests")
@Getter
@Setter
public class DroneDeliveryRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    /// Ô DRONE khách chọn (tuỳ chọn — đội bay có thể đổi lúc thả hàng).
    @Column(name = "box_id")
    private Long boxId;

    @Column(name = "box_number")
    private Integer boxNumber;

    @Column(name = "requester_user_id", nullable = false)
    private Long requesterUserId;

    @Column(name = "receiver_phone", length = 50)
    private String receiverPhone;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "drone_unit_id")
    private Long droneUnitId;

    @Column(name = "dispatched_by")
    private Long dispatchedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
