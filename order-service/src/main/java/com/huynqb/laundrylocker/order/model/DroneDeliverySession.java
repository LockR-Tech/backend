package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

/// Một phiên giao drone ↔ tủ của một đơn: nhận diện ArUco → mở nắp → drone nhả hàng → đóng nắp.
/// Lưu database để kết quả nắp của tủ chỉ được chấp nhận khi khớp mã lệnh đã gửi, và restart không mất phiên.
@Entity
@Table(name = "drone_delivery_sessions", schema = "order_schema")
@Getter
@Setter
public class DroneDeliverySession {

    public static final String MARKER_VALIDATED = "MARKER_VALIDATED";
    public static final String OPEN_LID_REQUESTED = "OPEN_LID_REQUESTED";
    public static final String LID_OPENED = "LID_OPENED";
    public static final String CLOSE_LID_REQUESTED = "CLOSE_LID_REQUESTED";
    public static final String READY_FOR_PICKUP = "READY_FOR_PICKUP";
    public static final String LID_OPEN_FAILED = "LID_OPEN_FAILED";
    public static final String LID_CLOSE_FAILED = "LID_CLOSE_FAILED";
    public static final String ABORTED = "ABORTED";

    /// Phiên còn chạy: chỉ một phiên như vậy cho mỗi đơn.
    public static final Set<String> ACTIVE_STATES =
            Set.of(MARKER_VALIDATED, OPEN_LID_REQUESTED, LID_OPENED, CLOSE_LID_REQUESTED);

    @Id
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "mission_id", nullable = false)
    private Long missionId;

    @Column(name = "drone_id", nullable = false, length = 50)
    private String droneId;

    @Column(name = "drone_unit_id")
    private Long droneUnitId;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Column(name = "compartment_id", nullable = false)
    private Long compartmentId;

    @Column(name = "target_marker_id")
    private Integer targetMarkerId;

    @Column(name = "target_dictionary", length = 50)
    private String targetDictionary;

    /// INDOOR_ARUCO_DEMO | REAL_OUTDOOR
    @Column(name = "mode", nullable = false, length = 30)
    private String mode;

    @Column(name = "state", nullable = false, length = 30)
    private String state;

    @Column(name = "open_command_id", length = 64)
    private String openCommandId;

    @Column(name = "close_command_id", length = 64)
    private String closeCommandId;

    @Column(name = "lid_opened_at")
    private LocalDateTime lidOpenedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return ACTIVE_STATES.contains(state);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
