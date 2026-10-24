package com.huynqb.laundrylocker.iot.service;

import com.huynqb.laundrylocker.iot.model.BleSigningKey;
import com.huynqb.laundrylocker.iot.repository.BleSigningKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;

/// Ký mã mở ô qua Bluetooth (F3-G11). Mã là 91 byte, big-endian — Pi giải đúng thứ tự này
/// (iot `services/ble_open_token.py`, docs/01-overview/ble-open-contract.md):
///
/// | byte | trường |
/// |---|---|
/// | 0 | phiên bản = 1 |
/// | 1–4 | lockerId (u32) |
/// | 5–6 | boxNumber (u16) |
/// | 7–14 | userId (u64) |
/// | 15–18 | hết hạn, giây Unix (u32) |
/// | 19–26 | nonce ngẫu nhiên — Pi không nhận lại nonce đã dùng |
/// | 27–90 | chữ ký Ed25519 trên byte 0–26 |
///
/// Cặp khoá sinh lần đầu cần và lưu trong `ble_signing_keys`; Pi chỉ biết khoá công khai.
@Slf4j
@Component
@RequiredArgsConstructor
public class BleTokenSigner {

    public static final byte VERSION = 1;
    public static final int PAYLOAD_LENGTH = 27;
    public static final int SIGNATURE_LENGTH = 64;
    static final String ALGORITHM = "Ed25519";
    /// Phần đầu cố định của khoá công khai Ed25519 dạng X.509; 32 byte sau nó là khoá thô.
    private static final int X509_PREFIX_LENGTH = 12;

    private final BleSigningKeyRepository repository;
    private final SecureRandom random = new SecureRandom();
    private volatile Loaded loaded;

    private record Loaded(PrivateKey privateKey, String publicKey) {
    }

    /// Mã đã ký kèm nonce (hex) để app đối chiếu kết quả Pi báo về.
    public record SignedToken(byte[] bytes, String nonceHex) {
        public String base64Url() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
    }

    /// Khoá công khai thô (32 byte) dạng base64 — Pi dùng để kiểm chữ ký.
    public String publicKeyBase64() {
        return keys().publicKey();
    }

    public SignedToken sign(long lockerId, int boxNumber, long userId, Instant expiresAt) {
        if (lockerId <= 0 || lockerId > 0xFFFFFFFFL || boxNumber <= 0 || boxNumber > 0xFFFF || userId <= 0) {
            throw new IllegalArgumentException("lockerId/boxNumber/userId out of range");
        }
        byte[] nonce = new byte[8];
        random.nextBytes(nonce);
        byte[] payload = ByteBuffer.allocate(PAYLOAD_LENGTH)
                .put(VERSION)
                .putInt((int) lockerId)
                .putShort((short) boxNumber)
                .putLong(userId)
                .putInt((int) expiresAt.getEpochSecond())
                .put(nonce)
                .array();
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(keys().privateKey());
            signer.update(payload);
            byte[] signature = signer.sign();
            byte[] token = Arrays.copyOf(payload, PAYLOAD_LENGTH + SIGNATURE_LENGTH);
            System.arraycopy(signature, 0, token, PAYLOAD_LENGTH, SIGNATURE_LENGTH);
            return new SignedToken(token, java.util.HexFormat.of().formatHex(nonce));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot sign BLE open token", ex);
        }
    }

    private Loaded keys() {
        Loaded current = loaded;
        if (current == null) {
            synchronized (this) {
                current = loaded;
                if (current == null) {
                    current = load(repository.findById(BleSigningKey.SINGLETON_ID).orElseGet(this::generate));
                    loaded = current;
                }
            }
        }
        return current;
    }

    private BleSigningKey generate() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
            byte[] x509 = pair.getPublic().getEncoded();
            BleSigningKey key = new BleSigningKey();
            key.setId(BleSigningKey.SINGLETON_ID);
            key.setAlgorithm(ALGORITHM);
            key.setPrivateKey(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            key.setPublicKey(Base64.getEncoder().encodeToString(
                    Arrays.copyOfRange(x509, X509_PREFIX_LENGTH, x509.length)));
            key.setCreatedAt(LocalDateTime.now());
            log.info("Generated the BLE open-token signing key");
            return repository.saveAndFlush(key);
        } catch (DataIntegrityViolationException race) {
            // Phiên bản khác vừa sinh cùng lúc: dùng khoá của nó.
            return repository.findById(BleSigningKey.SINGLETON_ID).orElseThrow(() -> race);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot generate BLE signing key", ex);
        }
    }

    private static Loaded load(BleSigningKey key) {
        try {
            PrivateKey privateKey = KeyFactory.getInstance(ALGORITHM).generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key.getPrivateKey())));
            return new Loaded(privateKey, key.getPublicKey());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Stored BLE signing key is unreadable", ex);
        }
    }
}
