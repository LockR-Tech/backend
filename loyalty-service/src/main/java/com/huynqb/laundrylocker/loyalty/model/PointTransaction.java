package com.huynqb.laundrylocker.loyalty.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "point_transactions")
@Getter
@Setter
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false)
    private Integer points;

    @Column(nullable = false, length = 30)
    private String type;

    /// Số tiền đơn được giảm nhờ giao dịch dùng điểm (ORDER_REDEEM / ORDER_REDEEM_REFUND); null với loại khác.
    @Column(precision = 14, scale = 2)
    private java.math.BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
