package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class LockerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true)
    private String orderCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "receiver_id")
    private Long receiverId;

    @Column(name = "receiver_user_id")
    private Long receiverUserId;

    @Column(name = "receiver_phone")
    private String receiverPhone;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(name = "locker_id")
    private Long lockerId;

    @Column(name = "destination_locker_id")
    private Long destinationLockerId;

    @Column(name = "send_box_id")
    private Long sendBoxId;

    @Column(name = "reserved_box_id")
    private Long reservedBoxId;

    @Column(name = "receive_box_id")
    private Long receiveBoxId;

    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(nullable = false, length = 30)
    private String type = "STORAGE";

    @Column(name = "service_category", length = 30)
    private String serviceCategory = "STORAGE";

    @Column(nullable = false, length = 30)
    private String status = "INITIALIZED";

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus = "UNPAID"; // UNPAID | PAID | REFUNDED

    @Column(name = "delivery_stage", length = 40)
    private String deliveryStage;

    @Column(name = "fulfillment_mode", length = 20)
    private String fulfillmentMode = "STANDARD";

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "actual_weight", precision = 10, scale = 2)
    private BigDecimal actualWeight;

    @Column(name = "parcel_weight_grams")
    private Integer parcelWeightGrams;

    @Column(name = "weight_unit", length = 20)
    private String weightUnit = "kg";

    @Column(name = "extra_fee", precision = 12, scale = 2)
    private BigDecimal extraFee = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "reservation_fee", precision = 12, scale = 2)
    private BigDecimal reservationFee = BigDecimal.ZERO;

    @Column(name = "storage_price", precision = 12, scale = 2)
    private BigDecimal storagePrice = BigDecimal.ZERO;

    @Column(name = "shipping_fee", precision = 12, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "total_price", precision = 12, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Column(name = "original_price", precision = 12, scale = 2)
    private BigDecimal originalPrice = BigDecimal.ZERO;

    @Column(name = "promotion_code")
    private String promotionCode;

    @Column(name = "applied_promotion_codes")
    private String appliedPromotionCodes;

    @Column(name = "pin_code")
    private String pinCode;

    @Column(name = "pin_code_issued_at")
    private LocalDateTime pinCodeIssuedAt;

    @Column(name = "receive_at")
    private LocalDateTime receiveAt;

    @Column(name = "intended_receive_at")
    private LocalDateTime intendedReceiveAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "pickup_deadline")
    private LocalDateTime pickupDeadline;

    @Column(name = "rental_duration_hours")
    private Integer rentalDurationHours;

    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;

    @Column(length = 2000)
    private String description;

    @Column(name = "customer_note", length = 1000)
    private String customerNote;

    @Column(name = "staff_note", length = 1000)
    private String staffNote;

    @Column(name = "cancel_reason")
    private Integer cancelReason;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

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
}
