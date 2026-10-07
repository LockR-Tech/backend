package com.huynqb.laundrylocker.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

/// Một bản tin `lockr/drones/{droneId}/telemetry` do Pi trên drone gửi lên — schemaVersion 1
/// (docs/01-overview/drone-telemetry-contract.md; phía gửi: `iot/drone-iot/lockr_drone/telemetry.py`).
///
/// Mỗi nhóm số đo kèm `ageMs` và `stale`: Pi vẫn gửi giá trị cuối cùng khi autopilot im, nên
/// mọi phán đoán ở đây chỉ dùng nhóm còn mới. Field Pi chưa đọc được thì null.
@JsonIgnoreProperties(ignoreUnknown = true)
public record DroneTelemetryFrame(
        Integer schemaVersion,
        String droneId,
        Long sequence,
        /// Thời điểm Pi chụp số đo, ISO-8601 UTC.
        String observedAt,
        Link link,
        Position position,
        Gps gps,
        Battery battery,
        Velocity velocity,
        Flight flight,
        Landed landed) {

    private static final Set<String> AIRBORNE_STATES = Set.of("IN_AIR", "TAKEOFF", "LANDING");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Link(String mavlink, Long heartbeatAgeMs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Position(Double lat, Double lng, Double relativeAltM, Double headingDeg, Long ageMs, Boolean stale) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Gps(Integer fixType, Integer satellites, Long ageMs, Boolean stale) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Battery(Integer percent, Double voltageV, Double currentA, Long ageMs, Boolean stale) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Velocity(Double groundSpeedMs, Double climbMs, Long ageMs, Boolean stale) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Flight(String mode, Boolean armed, String systemStatus, Long ageMs, Boolean stale) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Landed(String landedState, Long ageMs, Boolean stale) {
    }

    /// `observedAt` dạng epoch mili giây; null nếu thiếu hoặc sai định dạng.
    public Long observedAtMs() {
        if (observedAt == null) {
            return null;
        }
        try {
            return Instant.parse(observedAt).toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /// Pi đang nghe được autopilot. Mất heartbeat thì mọi nhóm số đo đều là số cũ.
    public boolean autopilotConnected() {
        return link != null && "connected".equals(link.mavlink());
    }

    /// Autopilot tự báo đã rời mặt đất (cất cánh, đang bay hoặc đang hạ).
    public boolean airborne() {
        return landedStateIs(AIRBORNE_STATES);
    }

    /// Autopilot tự báo đang đậu trên mặt đất. Không suy từ "chưa arm" hay "độ cao gần 0":
    /// thiếu báo cáo này thì coi như chưa biết.
    public boolean onGround() {
        return landedStateIs(Set.of("ON_GROUND"));
    }

    private boolean landedStateIs(Set<String> states) {
        return autopilotConnected()
                && landed != null
                && fresh(landed.stale())
                && landed.landedState() != null
                && states.contains(landed.landedState());
    }

    /// Có toạ độ dùng được: còn mới và GPS đã khoá 3D.
    public boolean hasPosition() {
        return autopilotConnected()
                && position != null && fresh(position.stale())
                && position.lat() != null && position.lng() != null
                && gps != null && fresh(gps.stale())
                && gps.fixType() != null && gps.fixType() >= 3;
    }

    public Double lat() {
        return position == null ? null : position.lat();
    }

    public Double lng() {
        return position == null ? null : position.lng();
    }

    public Double headingDeg() {
        return position == null ? null : position.headingDeg();
    }

    public Double groundSpeedMs() {
        return velocity == null || !fresh(velocity.stale()) ? null : velocity.groundSpeedMs();
    }

    /// Phần trăm pin, chỉ khi đo từ pin thật: drone cấp điện qua USB báo 0 V kèm một con số
    /// phần trăm vô nghĩa.
    public Integer batteryPercent() {
        if (!autopilotConnected() || battery == null || !fresh(battery.stale()) || battery.percent() == null) {
            return null;
        }
        if (battery.voltageV() == null || battery.voltageV() < 1.0) {
            return null;
        }
        return battery.percent() < 0 || battery.percent() > 100 ? null : battery.percent();
    }

    private static boolean fresh(Boolean stale) {
        return Boolean.FALSE.equals(stale);
    }
}
