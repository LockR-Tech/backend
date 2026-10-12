package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Một lần KTV drone tới nhà người gửi lấy kiện cho một đơn drone. Tạo khi đơn được thanh toán.
@Entity
@Table(name = "drone_pickups")
@Getter
@Setter
public class DronePickup {

    /// Chưa có KTV: đang tìm người rảnh gần người gửi nhất.
    public static final String SEARCHING = "SEARCHING";
    /// Đã mời một KTV, chờ người đó nhận hoặc từ chối trước `offerExpiresAt`.
    public static final String OFFERED = "OFFERED";
    /// KTV đã nhận việc và đang tới điểm lấy hàng.
    public static final String ACCEPTED = "ACCEPTED";
    /// KTV đã cầm kiện.
    public static final String PICKED_UP = "PICKED_UP";
    /// Tới nơi nhưng không lấy được (khách vắng, kiện sai khai báo…) — đơn giao không hoàn tất.
    public static final String FAILED = "FAILED";
    /// Đơn đóng trước khi lấy hàng.
    public static final String CANCELED = "CANCELED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "technician_id")
    private Long technicianId;

    @Column(name = "technician_distance_meters")
    private Integer technicianDistanceMeters;

    /// Mã người gửi đọc cho KTV lúc giao kiện.
    @Column(name = "pickup_code", nullable = false, length = 6)
    private String pickupCode;

    @Column(name = "offered_at")
    private LocalDateTime offeredAt;

    @Column(name = "offer_expires_at")
    private LocalDateTime offerExpiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "assigned_by_user_id")
    private Long assignedByUserId;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason", length = 40)
    private String failureReason;

    @Column(name = "failure_note", length = 500)
    private String failureNote;

    /// Ghi chú của KTV lúc lấy hàng.
    @Column(name = "pickup_note", length = 500)
    private String pickupNote;

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

    /// KTV đang bị lần lấy hàng này giữ chân (được mời hoặc đã nhận, chưa lấy xong).
    public boolean holdsTechnician() {
        return OFFERED.equals(status) || ACCEPTED.equals(status);
    }
}
