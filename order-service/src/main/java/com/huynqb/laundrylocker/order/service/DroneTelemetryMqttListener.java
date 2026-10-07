package com.huynqb.laundrylocker.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.order.dto.DroneTelemetryFrame;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Nhận telemetry drone qua MQTT — hợp đồng: docs/01-overview/drone-telemetry-contract.md.
 * Đổi topic/payload/chữ ký ở đây ⇒ sửa `DroneTelemetryMqttListenerTest` và
 * test trong `iot/drone-iot/tests`.
 *
 * <p>Kết nối riêng, chỉ đọc `lockr/drones/+/telemetry`; không dùng chung gì với cầu MQTT của tủ
 * (iot-service). Chỉ chạy khi `app.drone.telemetry.enabled=true`.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.drone.telemetry.enabled", havingValue = "true")
public class DroneTelemetryMqttListener {

    static final String SUBSCRIPTION = "lockr/drones/+/telemetry";
    static final String SIGNATURE_PROPERTY = "sig";
    static final long CONNECT_RETRY_SECONDS = 15;
    /// Cùng ràng buộc với tên tài khoản broker: mã drone nằm trong topic nên không được chứa `/`, `+`, `#`.
    private static final Pattern DRONE_CODE = Pattern.compile("[A-Za-z0-9_-]{1,50}");

    private final DroneTelemetryService telemetryService;
    private final ObjectMapper objectMapper;
    private final String brokerUrl;
    private final String clientId;
    private final String username;
    private final String password;
    private final String secret;
    private final long maxClockSkewMs;

    private MqttClient client;
    /// Telemetry cũ không còn giá trị: hàng đợi ngắn, đầy thì bỏ bản tin cũ nhất thay vì
    /// dồn ứ khi database hay service khác chậm.
    private final ExecutorService inbound = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(64),
            runnable -> {
                Thread thread = new Thread(runnable, "drone-telemetry");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());
    /// Lần kết nối đầu hỏng (broker chưa lên) thì Paho không tự thử lại — tự lên lịch thử.
    private final ScheduledExecutorService connector = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "drone-telemetry-connect");
        thread.setDaemon(true);
        return thread;
    });

    public DroneTelemetryMqttListener(
            DroneTelemetryService telemetryService,
            ObjectMapper objectMapper,
            @Value("${app.drone.telemetry.broker-url}") String brokerUrl,
            @Value("${app.drone.telemetry.client-id:lockr-order-drone-telemetry}") String clientId,
            @Value("${app.drone.telemetry.username:}") String username,
            @Value("${app.drone.telemetry.password:}") String password,
            @Value("${app.drone.telemetry.secret:}") String secret,
            @Value("${app.drone.telemetry.max-clock-skew-seconds:120}") long maxClockSkewSeconds) {
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.secret = secret;
        this.maxClockSkewMs = maxClockSkewSeconds * 1000;
    }

    @PostConstruct
    public void init() {
        // Telemetry quyết định lúc nào cấp mã mở ô: không nhận từ broker ai cũng ghi được
        // mà lại không kiểm chữ ký.
        if (!StringUtils.hasText(secret) && !StringUtils.hasText(username)) {
            log.error("Drone telemetry is enabled but neither DRONE_TELEMETRY_SECRET nor a broker account is set"
                    + " — refusing to listen on {}", brokerUrl);
            return;
        }
        try {
            client = new MqttClient(brokerUrl, clientId + "-" + System.nanoTime(), new MemoryPersistence());
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setAutomaticReconnect(true);
            options.setCleanStart(true);
            if (StringUtils.hasText(username)) {
                options.setUserName(username);
                options.setPassword(password.getBytes(StandardCharsets.UTF_8));
            }
            // Paho mqttv5 1.2.5: subscribe(...) kèm IMqttMessageListener bị đệ quy vô hạn,
            // nên nhận bản tin qua callback toàn cục (giống cầu MQTT của tủ).
            client.setCallback(new MqttCallback() {
                @Override
                public void disconnected(MqttDisconnectResponse response) {
                    log.warn("Drone telemetry MQTT disconnected: {}", response.getReasonString());
                }

                @Override
                public void mqttErrorOccurred(MqttException exception) {
                    log.warn("Drone telemetry MQTT error: {}", exception.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    onMessage(topic, new String(message.getPayload(), StandardCharsets.UTF_8), signatureOf(message));
                }

                @Override
                public void deliveryComplete(IMqttToken token) {
                }

                @Override
                public void connectComplete(boolean reconnect, String serverUri) {
                    if (reconnect) {
                        // Clean start: broker quên subscription khi mất kết nối.
                        subscribe();
                    }
                }

                @Override
                public void authPacketArrived(int reasonCode, MqttProperties properties) {
                }
            });
            connectOrRetry(options);
        } catch (MqttException e) {
            log.error("Failed to create drone telemetry MQTT client for {}", brokerUrl, e);
        }
    }

    private void connectOrRetry(MqttConnectionOptions options) {
        try {
            client.connect(options);
            log.info("Drone telemetry connected to MQTT broker {} (signature {})",
                    brokerUrl, StringUtils.hasText(secret) ? "required" : "not required");
            subscribe();
        } catch (MqttException e) {
            log.error("Drone telemetry MQTT connect to {} failed ({}) — retry in {} s",
                    brokerUrl, e.getMessage(), CONNECT_RETRY_SECONDS);
            connector.schedule(() -> connectOrRetry(options), CONNECT_RETRY_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void subscribe() {
        try {
            client.subscribe(SUBSCRIPTION, 1);
            log.info("Subscribed to MQTT topic {}", SUBSCRIPTION);
        } catch (MqttException e) {
            log.error("Drone telemetry MQTT subscribe failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        connector.shutdownNow();
        inbound.shutdownNow();
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
            } catch (MqttException e) {
                log.warn("Error disconnecting drone telemetry MQTT client", e);
            }
        }
    }

    private static String signatureOf(MqttMessage message) {
        MqttProperties properties = message.getProperties();
        List<UserProperty> userProperties = properties == null ? null : properties.getUserProperties();
        if (userProperties == null) {
            return null;
        }
        return userProperties.stream()
                .filter(property -> SIGNATURE_PROPERTY.equals(property.getKey()))
                .map(UserProperty::getValue)
                .findFirst()
                .orElse(null);
    }

    /// Kiểm topic, chữ ký và độ tươi trên luồng callback (rẻ), rồi đẩy phần đụng database
    /// sang `inbound`.
    void onMessage(String topic, String payload, String signature) {
        DroneTelemetryFrame frame = accept(topic, payload, signature, System.currentTimeMillis());
        if (frame != null) {
            String droneCode = droneCodeOf(topic);
            inbound.execute(() -> telemetryService.ingest(droneCode, frame));
        }
    }

    /// Trả bản tin nếu hợp lệ, null nếu phải bỏ.
    DroneTelemetryFrame accept(String topic, String payload, String signature, long nowMs) {
        String droneCode = droneCodeOf(topic);
        if (droneCode == null) {
            return null;
        }
        if (StringUtils.hasText(secret)
                && !DroneTelemetrySigner.verify(secret, droneCode, topic, payload, signature)) {
            // debug: trên broker công khai người lạ cũng ghi được vào topic này.
            log.debug("Drone telemetry on {} rejected: bad or missing signature", topic);
            return null;
        }
        DroneTelemetryFrame frame;
        try {
            frame = objectMapper.readValue(payload, DroneTelemetryFrame.class);
        } catch (Exception e) {
            log.warn("Drone telemetry on {} rejected: {}", topic, e.getMessage());
            return null;
        }
        if (frame == null || (!droneCode.equals(frame.droneId()))) {
            log.warn("Drone telemetry on {} rejected: droneId does not match topic", topic);
            return null;
        }
        // Chặn phát lại bản tin cũ đã ký. Đồng hồ Pi lệch quá ngưỡng thì mọi bản tin bị bỏ:
        // Pi phải đồng bộ NTP.
        Long observedAtMs = frame.observedAtMs();
        if (observedAtMs == null || Math.abs(nowMs - observedAtMs) > maxClockSkewMs) {
            log.warn("Drone telemetry on {} rejected: observedAt {} outside +/-{} s of server time",
                    topic, frame.observedAt(), maxClockSkewMs / 1000);
            return null;
        }
        return frame;
    }

    /// `lockr/drones/{droneId}/telemetry` ⇒ `droneId`; topic khác dạng ⇒ null.
    static String droneCodeOf(String topic) {
        String[] segments = topic == null ? new String[0] : topic.split("/");
        if (segments.length != 4
                || !"lockr".equals(segments[0])
                || !"drones".equals(segments[1])
                || !"telemetry".equals(segments[3])) {
            return null;
        }
        return DRONE_CODE.matcher(segments[2]).matches() ? segments[2] : null;
    }
}
