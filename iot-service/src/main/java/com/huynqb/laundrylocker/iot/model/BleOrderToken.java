package com.huynqb.laundrylocker.iot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Mã mở ô qua Bluetooth đã cấp cho một đơn (F3-G11): `nonce` của mã → đơn/ô/tài khoản. Pi báo đã mở kèm
/// `nonce` thì backend mới coi ô của đơn là đã mở; `processedAt` khác null ⇒ đã xử lý (chống lặp).
@Entity
@Table(name = "ble_order_tokens")
@Getter
@Setter
public class BleOrderToken {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";
    public static final String RESULT_JAMMED = "JAMMED";

    /// 8 byte ngẫu nhiên trong mã, hex chữ thường.
    @Id
    @Column(length = 16)
    private String nonce;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "locker_id", nullable = false)
    private Long lockerId;

    @Column(name = "box_id", nullable = false)
    private Long boxId;

    @Column(name = "box_number", nullable = false)
    private Integer boxNumber;

    /// Tài khoản xin mã (chủ đơn, người thuê, người nhận hoặc người lấy hộ).
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /// DROP | RENTAL_ACCESS | PICKUP.
    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /// SUCCESS | FAILED | JAMMED.
    @Column(length = 20)
    private String result;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
