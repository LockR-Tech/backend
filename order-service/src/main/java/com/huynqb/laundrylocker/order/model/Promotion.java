package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotions")
@Getter
@Setter
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "discount_type", nullable = false, length = 30)
    private String discountType = "FIXED_AMOUNT";

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_order_amount", precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(nullable = false)
    private Boolean stackable = false;

    /// Scope theo tủ/kiosk: null = áp dụng toàn hệ thống.
    @Column(name = "locker_id")
    private Long lockerId;

    /// Trần tổng lượt dùng: null = không giới hạn.
    @Column(name = "total_usage_limit")
    private Integer totalUsageLimit;

    /// Trần lượt dùng mỗi user: null = không giới hạn.
    @Column(name = "per_user_limit")
    private Integer perUserLimit;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

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

    public boolean activeNow() {
        LocalDateTime now = LocalDateTime.now();
        return "ACTIVE".equalsIgnoreCase(status)
                && (startAt == null || !now.isBefore(startAt))
                && (endAt == null || !now.isAfter(endAt));
    }
}
