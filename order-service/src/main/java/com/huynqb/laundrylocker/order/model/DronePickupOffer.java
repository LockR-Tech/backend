package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Một lượt mời KTV cho một lần lấy hàng — để không mời lại người đã từ chối hoặc để quá hạn.
@Entity
@Table(name = "drone_pickup_offers")
@Getter
@Setter
public class DronePickupOffer {

    public static final String OFFERED = "OFFERED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";
    public static final String EXPIRED = "EXPIRED";
    /// KTV đã nhận nhưng không tới lấy trong hạn, hoặc admin gán người khác.
    public static final String RELEASED = "RELEASED";
    public static final String CANCELED = "CANCELED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pickup_id", nullable = false)
    private Long pickupId;

    @Column(name = "technician_id", nullable = false)
    private Long technicianId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "offered_at", nullable = false)
    private LocalDateTime offeredAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
