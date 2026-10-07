package com.huynqb.laundrylocker.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.order.dto.DroneTelemetryFrame;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/// Hợp đồng với Pi trên drone (docs/01-overview/drone-telemetry-contract.md). Đổi ở đây ⇒
/// sửa `iot/drone-iot/tests/test_signing.py` và `test_telemetry.py`.
class DroneTelemetryMqttListenerTest {

    private static final String SECRET = "test-master-secret";
    private static final String DRONE = "DRONE-S550-01";
    private static final String TOPIC = "lockr/drones/DRONE-S550-01/telemetry";
    private static final long NOW = Instant.parse("2026-10-04T08:25:36.000Z").toEpochMilli();

    /// Một bản tin thật do Pi in ra khi drone nằm trên bàn, trong nhà (chưa có GPS, cấp điện qua USB).
    private static final String BENCH_PAYLOAD = """
            {"schemaVersion": 1, "droneId": "DRONE-S550-01", "sequence": 2, "observedAt": "2026-10-04T08:25:35.946Z", \
            "link": {"mavlink": "connected", "heartbeatAgeMs": 642}, \
            "position": {"lat": null, "lng": null, "relativeAltM": 0.48, "headingDeg": 247.4, "ageMs": 109, "stale": false}, \
            "gps": {"fixType": 1, "satellites": 0, "ageMs": 109, "stale": false}, \
            "battery": {"percent": 98, "voltageV": 0.0, "currentA": 0.53, "ageMs": 109, "stale": false}, \
            "velocity": {"groundSpeedMs": 0.01, "climbMs": 0.01, "ageMs": 109, "stale": false}, \
            "flight": {"mode": "STABILIZE", "armed": false, "systemStatus": "STANDBY", "ageMs": 642, "stale": false}, \
            "landed": {"landedState": "ON_GROUND", "ageMs": 109, "stale": false}, "warnings": []}""";

    private final DroneTelemetryMqttListener listener = listener(SECRET);

    private static DroneTelemetryMqttListener listener(String secret) {
        return new DroneTelemetryMqttListener(
                mock(DroneTelemetryService.class), new ObjectMapper(),
                "tcp://localhost:1883", "test", "", "", secret, 120);
    }

    private static String signed(String topic, String payload) {
        return DroneTelemetrySigner.sign(DroneTelemetrySigner.deviceKey(SECRET, DRONE), topic, payload);
    }

    @Test
    void signatureMatchesTheVectorThePiSideIsTestedAgainst() {
        String payload = "{\"schemaVersion\": 1, \"droneId\": \"DRONE-S550-01\", \"sequence\": 1}";
        String key = DroneTelemetrySigner.deviceKey(SECRET, DRONE);

        assertEquals("22e3e9b5237ea3f838e29f419f2da40d1850e21d7e4592fc606ea2ad981c1631", key);
        assertEquals(
                "cb7a596f42ae3b100cfc020a4201ac032fd8bc304e6d94b81a95e6121926819b",
                DroneTelemetrySigner.sign(key, TOPIC, payload));
    }

    @Test
    void acceptsASignedFrameAndReadsItTheWayThePiMeantIt() {
        DroneTelemetryFrame frame = listener.accept(TOPIC, BENCH_PAYLOAD, signed(TOPIC, BENCH_PAYLOAD), NOW);

        assertNotNull(frame);
        assertEquals(DRONE, frame.droneId());
        assertEquals(Instant.parse("2026-10-04T08:25:35.946Z").toEpochMilli(), frame.observedAtMs());
        assertTrue(frame.autopilotConnected());
        assertTrue(frame.onGround());
        assertFalse(frame.airborne());
        // Trong nhà: GPS chưa khoá ⇒ không có vị trí; cấp điện qua USB (0 V) ⇒ không tin phần trăm pin.
        assertFalse(frame.hasPosition());
        assertNull(frame.batteryPercent());
    }

    @Test
    void rejectsUnsignedTamperedOrForeignFrames() {
        String signature = signed(TOPIC, BENCH_PAYLOAD);

        assertNull(listener.accept(TOPIC, BENCH_PAYLOAD, null, NOW));
        assertNull(listener.accept(TOPIC, BENCH_PAYLOAD.replace("ON_GROUND", "IN_AIR"), signature, NOW));
        // Chữ ký của drone này không dùng được trên topic của drone khác.
        assertNull(listener.accept("lockr/drones/DRONE-02/telemetry", BENCH_PAYLOAD, signature, NOW));
    }

    @Test
    void rejectsAFrameWhoseDroneIdDisagreesWithItsTopic() {
        String topic = "lockr/drones/DRONE-02/telemetry";
        String signature = DroneTelemetrySigner.sign(
                DroneTelemetrySigner.deviceKey(SECRET, "DRONE-02"), topic, BENCH_PAYLOAD);

        assertNull(listener.accept(topic, BENCH_PAYLOAD, signature, NOW));
    }

    @Test
    void rejectsAReplayedOldFrame() {
        long tenMinutesLater = NOW + 600_000;

        assertNull(listener.accept(TOPIC, BENCH_PAYLOAD, signed(TOPIC, BENCH_PAYLOAD), tenMinutesLater));
    }

    @Test
    void onlyTheTelemetryTopicOfAWellFormedDroneCodeIsRead() {
        assertEquals(DRONE, DroneTelemetryMqttListener.droneCodeOf(TOPIC));
        assertNull(DroneTelemetryMqttListener.droneCodeOf("lockr/drones/DRONE-S550-01/status"));
        assertNull(DroneTelemetryMqttListener.droneCodeOf("lockr/drones/a b/telemetry"));
        assertNull(DroneTelemetryMqttListener.droneCodeOf("cabinet/1/heartbeat"));
    }

    @Test
    void staleLandedStateIsNeitherAirborneNorOnGround() {
        String payload = BENCH_PAYLOAD.replace(
                "\"landedState\": \"ON_GROUND\", \"ageMs\": 109, \"stale\": false",
                "\"landedState\": \"ON_GROUND\", \"ageMs\": 9000, \"stale\": true");

        DroneTelemetryFrame frame = listener.accept(TOPIC, payload, signed(TOPIC, payload), NOW);

        assertNotNull(frame);
        assertFalse(frame.onGround());
        assertFalse(frame.airborne());
    }
}
