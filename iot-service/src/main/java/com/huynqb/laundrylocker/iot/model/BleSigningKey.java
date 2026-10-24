package com.huynqb.laundrylocker.iot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Cặp khoá Ed25519 ký mã mở ô qua Bluetooth. Chỉ một dòng (`id = 1`); Pi tải khoá công khai qua
/// `GET /api/iot/ble/public-key`.
@Entity
@Table(name = "ble_signing_keys")
@Getter
@Setter
public class BleSigningKey {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 20)
    private String algorithm;

    /// PKCS#8, base64.
    @Column(name = "private_key", nullable = false, columnDefinition = "TEXT")
    private String privateKey;

    /// 32 byte khoá công khai thô, base64.
    @Column(name = "public_key", nullable = false, length = 64)
    private String publicKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
