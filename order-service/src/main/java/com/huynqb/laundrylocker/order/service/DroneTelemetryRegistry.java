package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.dto.DroneTelemetryFrame;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// Bản tin telemetry mới nhất của từng drone, giữ trong bộ nhớ: chỉ để biết drone còn
/// tín hiệu hay không và chặn bản tin cũ. Khởi động lại service thì bản tin kế tiếp điền lại.
@Component
public class DroneTelemetryRegistry {

    private final long liveWindowMs;
    private final Map<String, Entry> latest = new ConcurrentHashMap<>();

    public DroneTelemetryRegistry(@Value("${app.drone.telemetry.live-window-seconds:15}") long liveWindowSeconds) {
        this.liveWindowMs = liveWindowSeconds * 1000;
    }

    /**
     * Ghi nhận một bản tin. Trả false nếu nó không mới hơn bản đã nhận (tới trễ, hoặc bị
     * phát lại) — trừ khi drone đã im quá khoảng "còn tín hiệu", lúc đó coi như phiên mới
     * (Pi khởi động lại, đồng hồ vừa được chỉnh).
     */
    public boolean accept(String droneCode, DroneTelemetryFrame frame, long nowMs) {
        boolean[] accepted = {false};
        latest.compute(droneCode, (code, previous) -> {
            boolean stale = previous != null
                    && nowMs - previous.receivedAtMs() <= liveWindowMs
                    && frame.observedAtMs() != null
                    && previous.frame().observedAtMs() != null
                    && frame.observedAtMs() <= previous.frame().observedAtMs();
            if (stale) {
                return previous;
            }
            accepted[0] = true;
            return new Entry(frame, nowMs);
        });
        return accepted[0];
    }

    public boolean isLive(String droneCode) {
        return isLive(droneCode, System.currentTimeMillis());
    }

    boolean isLive(String droneCode, long nowMs) {
        Entry entry = droneCode == null ? null : latest.get(droneCode);
        // Pi còn gửi nhưng không nghe được autopilot thì cũng là mất tín hiệu drone.
        return entry != null && nowMs - entry.receivedAtMs() <= liveWindowMs && entry.frame().autopilotConnected();
    }

    public Optional<DroneTelemetryFrame> latest(String droneCode) {
        Entry entry = droneCode == null ? null : latest.get(droneCode);
        return entry == null ? Optional.empty() : Optional.of(entry.frame());
    }

    private record Entry(DroneTelemetryFrame frame, long receivedAtMs) {
    }
}
