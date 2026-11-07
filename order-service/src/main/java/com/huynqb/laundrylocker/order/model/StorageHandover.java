package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Biên bản trao trả đồ lưu kho tại quầy — xem StorageHandoverService.
@Entity
@Table(name = "storage_handovers")
@Getter
@Setter
public class StorageHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    /// SENDER | RECEIVER | DELEGATE | OTHER
    @Column(name = "recipient_type", nullable = false, length = 20)
    private String recipientType;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "photo_public_id", nullable = false, unique = true)
    private String photoPublicId;

    @Column(name = "photo_url", nullable = false, length = 1000)
    private String photoUrl;

    /// Số tiền admin khai đã thu tại quầy (null = không thu gì).
    @Column(name = "collected_amount", precision = 12, scale = 2)
    private BigDecimal collectedAmount;

    /// CASH | TRANSFER
    @Column(name = "collection_method", length = 20)
    private String collectionMethod;

    @Column(length = 500)
    private String note;

    @Column(name = "handed_over_by_user_id", nullable = false)
    private Long handedOverByUserId;

    @Column(name = "handed_over_at", nullable = false)
    private LocalDateTime handedOverAt;
}
