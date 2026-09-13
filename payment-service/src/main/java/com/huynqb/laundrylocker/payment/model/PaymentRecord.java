package com.huynqb.laundrylocker.payment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false, length = 30)
    private String method = "CASH";

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "reference_id", unique = true)
    private String referenceId;

    @Column(name = "reference_transaction_id")
    private String referenceTransactionId;

    @Column(length = 500)
    private String content;

    @Column(length = 1000)
    private String qr;

    @Column(length = 1000)
    private String url;

    @Column(length = 1000)
    private String deeplink;

    @Column(length = 1000)
    private String description;

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
