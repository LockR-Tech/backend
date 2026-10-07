package com.huynqb.laundrylocker.order.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Chữ ký bản tin telemetry drone (docs/01-overview/drone-telemetry-contract.md § 3).
 *
 * <p>Mỗi drone có khoá riêng suy từ khoá gốc của backend, nên lộ khoá một Pi không giả
 * được drone khác: {@code deviceKey = hex(HMAC-SHA256(secret, "lockr-drone:" + droneCode))}.
 * Pi ký từng bản tin: {@code sig = hex(HMAC-SHA256(deviceKey, topic + "\n" + payload))}.
 * Đổi công thức ở đây ⇒ sửa phần ký trong {@code iot/drone-iot} và test hai phía.
 */
public final class DroneTelemetrySigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String KEY_CONTEXT = "lockr-drone:";

    private DroneTelemetrySigner() {
    }

    public static String deviceKey(String secret, String droneCode) {
        return hmacHex(secret, KEY_CONTEXT + droneCode);
    }

    public static String sign(String deviceKey, String topic, String payload) {
        return hmacHex(deviceKey, topic + "\n" + payload);
    }

    /// So sánh thời gian hằng để không lộ chữ ký đúng qua thời gian phản hồi.
    public static boolean verify(String secret, String droneCode, String topic, String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = sign(deviceKey(secret, droneCode), topic, payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacHex(String key, String message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
