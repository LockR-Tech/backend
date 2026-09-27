package com.huynqb.laundrylocker.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huynqb.laundrylocker.iot.dto.BoxStatusUpdateRequest;
import com.huynqb.laundrylocker.iot.dto.DeviceStatusRequest;
import com.huynqb.laundrylocker.iot.settings.IotRules;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/// Cầu MQTT tới tủ. Hợp đồng topic/payload: docs/01-overview/mqtt-contract.md (ADR-0008).
/// Đổi payload ở đây ⇒ sửa `LockerMqttServiceTest` và `iot/tests/test_mqtt_contract.py`.
@Slf4j
@Service
@RequiredArgsConstructor
public class LockerMqttService {

    static final String[] SUBSCRIPTIONS = {
            "cabinet/+/command/+/result",
            "cabinet/+/heartbeat",
            "cabinet/+/locker/+/status",
            "iot/+/discovery/result",
            "iot/+/setup/progress",
            "iot/+/setup/result",
    };

    @Value("${mqtt.broker-url:tcp://broker.hivemq.com:1883}")
    private String brokerUrl;

    @Value("${mqtt.client-id:laundry-iot-service}")
    private String clientId;

    /// Broker riêng (SEC-04): tài khoản `iot-service`. Để trống = broker không xác thực.
    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;
    /// Thời gian cửa mở (`timeout` gửi xuống tủ) và thời gian chờ phản hồi do admin cấu hình (ADR-0005).
    private final IotRules rules;
    /// `slotIndex = boxNumber − 1` tra từ sơ đồ tủ của locker-service.
    private final CabinetLayoutLookup layoutLookup;
    private MqttClient client;
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingCommands = new ConcurrentHashMap<>();
    /// Trạng thái cửa, heartbeat, bản tin cấp phát có thể gọi Feign/DB — xử lý ngoài luồng
    /// callback của Paho để không làm chậm kết quả mở ô đang có người đứng chờ ở tủ.
    private final ExecutorService inbound = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mqtt-inbound");
        thread.setDaemon(true);
        return thread;
    });

    /// Lần kết nối đầu hỏng (broker chưa lên) thì Paho không tự thử lại — tự lên lịch thử.
    private final ScheduledExecutorService connector = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mqtt-connect");
        thread.setDaemon(true);
        return thread;
    });
    static final long CONNECT_RETRY_SECONDS = 15;

    @PostConstruct
    public void init() {
        try {
            client = new MqttClient(brokerUrl, clientId + "-" + System.nanoTime(), new MemoryPersistence());
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setAutomaticReconnect(true);
            options.setCleanStart(true);
            if (StringUtils.hasText(username)) {
                options.setUserName(username);
                options.setPassword(password.getBytes(StandardCharsets.UTF_8));
            }

            // Paho mqttv5 1.2.5: subscribe(...) với IMqttMessageListener bị bug đệ quy vô hạn
            // (StackOverflowError), nên phải nhận message qua setCallback toàn cục.
            client.setCallback(new org.eclipse.paho.mqttv5.client.MqttCallback() {
                @Override
                public void disconnected(org.eclipse.paho.mqttv5.client.MqttDisconnectResponse disconnectResponse) {
                    log.warn("MQTT disconnected: {}", disconnectResponse.getReasonString());
                }

                @Override
                public void mqttErrorOccurred(MqttException exception) {
                    log.warn("MQTT error: {}", exception.getMessage());
                }

                @Override
                public void deliveryComplete(org.eclipse.paho.mqttv5.client.IMqttToken token) {
                }

                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("MQTT connected (reconnect={}) to {}", reconnect, serverURI);
                    if (reconnect) {
                        // Clean start: broker quên subscription khi mất kết nối.
                        subscribeAll();
                    }
                }

                @Override
                public void authPacketArrived(int reasonCode, MqttProperties properties) {
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleMessage(topic, new String(message.getPayload(), StandardCharsets.UTF_8));
                }
            });

            connectOrRetry(options);
        } catch (MqttException e) {
            log.error("Failed to create MQTT client for {}", brokerUrl, e);
        }
    }

    private void connectOrRetry(MqttConnectionOptions options) {
        try {
            client.connect(options);
            log.info("Connected to MQTT Broker: {}", brokerUrl);
            subscribeAll();
        } catch (MqttException e) {
            log.error("MQTT connect to {} failed ({}) — retry in {} s", brokerUrl, e.getMessage(), CONNECT_RETRY_SECONDS);
            connector.schedule(() -> connectOrRetry(options), CONNECT_RETRY_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void subscribeAll() {
        try {
            int[] qos = new int[SUBSCRIPTIONS.length];
            java.util.Arrays.fill(qos, 1);
            client.subscribe(SUBSCRIPTIONS, qos);
            log.info("Subscribed to MQTT topics: {}", String.join(", ", SUBSCRIPTIONS));
        } catch (MqttException e) {
            log.error("MQTT subscribe failed: {}", e.getMessage());
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
                log.warn("Error disconnecting MQTT client", e);
            }
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    /// Kết quả lệnh (có người đang chờ) xử lý ngay trên luồng callback; phần còn lại
    /// đẩy sang `inbound`.
    void handleMessage(String topic, String payload) {
        try {
            log.debug("Received message on {}: {}", topic, payload);
            JsonNode data = objectMapper.readTree(payload);
            if (topic.startsWith("cabinet/") && topic.endsWith("/result")) {
                completeCommand(data);
            } else {
                inbound.execute(() -> route(topic, data));
            }
        } catch (Exception e) {
            log.error("Error parsing MQTT message on topic {}: {}", topic, e.getMessage());
        }
    }

    private void completeCommand(JsonNode data) {
        String commandId = text(data, "commandId");
        if (commandId == null) {
            return;
        }
        CompletableFuture<JsonNode> future = pendingCommands.remove(commandId);
        if (future != null) {
            future.complete(data);
        }
    }

    void route(String topic, JsonNode data) {
        try {
            String[] segments = topic.split("/");
            if (segments.length == 3 && "cabinet".equals(segments[0]) && "heartbeat".equals(segments[2])) {
                onHeartbeat(segments[1], data);
            } else if (segments.length == 5 && "cabinet".equals(segments[0])
                    && "locker".equals(segments[2]) && "status".equals(segments[4])) {
                onBoxStatus(parseLongOrNull(segments[1]), data);
            } else if (segments.length == 4 && "iot".equals(segments[0])) {
                GatewayProvisioningService provisioning = applicationContext.getBean(GatewayProvisioningService.class);
                String kind = segments[2] + "/" + segments[3];
                switch (kind) {
                    case "discovery/result" -> provisioning.onDiscovery(segments[1], data);
                    case "setup/progress" -> provisioning.onSetupProgress(segments[1], data);
                    case "setup/result" -> provisioning.onSetupResult(segments[1], data);
                    default -> log.debug("Unhandled MQTT topic {}", topic);
                }
            }
        } catch (Exception e) {
            log.error("Error handling MQTT message on topic {}: {}", topic, e.getMessage());
        }
    }

    /// `cabinet/{lockerId}/heartbeat` — thiết bị định danh bằng MAC trong payload; bản cũ/giả lập
    /// không có MAC thì lấy đoạn `{lockerId}` của topic.
    private void onHeartbeat(String topicLockerId, JsonNode data) {
        String mac = text(data, "macAddress");
        String deviceId = mac != null ? mac : topicLockerId;
        String status = data.hasNonNull("status") ? data.get("status").asText() : "ONLINE";
        applicationContext.getBean(IotService.class)
                .updateStatus(new DeviceStatusRequest(deviceId, parseLongOrNull(topicLockerId), status));
        if (mac != null) {
            applicationContext.getBean(GatewayProvisioningService.class).touch(mac);
        }
    }

    /// `cabinet/{lockerId}/locker/{slotIndex}/status` — ưu tiên `boxId` trong payload; Pi không
    /// biết boxId thì tra ô có `boxNumber = slotIndex + 1` trong tủ.
    private void onBoxStatus(Long lockerId, JsonNode data) {
        Long boxId = data.hasNonNull("boxId") ? data.get("boxId").asLong() : null;
        if (boxId == null && data.hasNonNull("slotIndex")) {
            boxId = layoutLookup.boxIdAt(lockerId, data.get("slotIndex").asInt()).orElse(null);
        }
        if (boxId == null) {
            log.warn("Box status from locker {} ignored: cannot resolve box ({})", lockerId, data);
            return;
        }
        String hwState = data.hasNonNull("hwState") ? data.get("hwState").asText() : "UNKNOWN";
        applicationContext.getBean(IotService.class)
                .updateBoxStatus(new BoxStatusUpdateRequest(boxId, hwState, lockerId));
    }

    private static Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String text(JsonNode data, String field) {
        return data.hasNonNull(field) ? data.get(field).asText() : null;
    }

    /// Gửi một bản tin QoS 1. Ném lỗi nếu chưa kết nối broker — người gọi quyết định xử lý.
    public void publish(String topic, JsonNode payload) throws MqttException {
        if (!isConnected()) {
            throw new IllegalStateException("MQTT client not connected");
        }
        MqttMessage message = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        client.publish(topic, message);
    }

    /// Booking → IoT sync (GAP 1): fire-and-forget notify the cabinet that a box's
    /// lifecycle state changed (RESERVED/OCCUPIED/AVAILABLE/FAULT). Unlike the open
    /// command this expects no hardware reply, so it never blocks the order flow and
    /// swallows any broker error (returns false instead of throwing).
    public boolean publishBoxStateSync(Long lockerId, Long boxId, String state, Long orderId) {
        if (client == null || !client.isConnected()) {
            log.warn("Skip box-state sync for locker {} box {}: MQTT client not connected", lockerId, boxId);
            return false;
        }
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("boxId", boxId);
            payload.put("state", state);
            if (orderId != null) {
                payload.put("orderId", orderId);
            }
            String topic = "cabinet/" + lockerId + "/command/sync";
            publish(topic, payload);
            log.info("Published box-state sync (box {} -> {}) to {}", boxId, state, topic);
            return true;
        } catch (Exception ex) {
            log.warn("MQTT box-state sync failed for locker {} box {}: {}", lockerId, boxId, ex.getMessage());
            return false;
        }
    }

    /// Lệnh mở gửi cả `boxId` lẫn `slotIndex` (ADR-0008). `box_id` giữ cho giả lập cũ.
    ObjectNode openCommandPayload(String commandId, Long lockerId, Long boxId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("commandId", commandId);
        payload.put("boxId", boxId);
        payload.put("box_id", boxId);
        layoutLookup.slotIndexOf(lockerId, boxId).ifPresentOrElse(
                slot -> payload.put("slotIndex", slot),
                () -> log.warn("No slotIndex for locker {} box {} — cabinet must map boxId itself", lockerId, boxId));
        payload.put("action", "OPEN");
        payload.put("timeout", rules.doorOpenTimeoutSeconds());
        return payload;
    }

    public CompletableFuture<JsonNode> sendUnlockCommandAsync(Long lockerId, Long boxId) {
        String commandId = UUID.randomUUID().toString();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingCommands.put(commandId, future);

        try {
            MqttMessage message = new MqttMessage(
                    openCommandPayload(commandId, lockerId, boxId).toString().getBytes(StandardCharsets.UTF_8));
            message.setQos(1);

            String topic = "cabinet/" + lockerId + "/command/open";
            client.publish(topic, message);
            log.info("Published OPEN command {} to {}", commandId, topic);

            // Auto cleanup future if not resolved in time
            future.orTimeout(rules.unlockWaitSeconds(), TimeUnit.SECONDS).whenComplete((res, ex) -> {
                if (ex != null) {
                    pendingCommands.remove(commandId);
                    log.warn("Command {} timed out", commandId);
                }
            });

        } catch (Exception ex) {
            log.warn("MQTT publish failed for locker {}: {}", lockerId, ex.getMessage());
            future.completeExceptionally(ex);
            pendingCommands.remove(commandId);
        }

        return future;
    }
}
