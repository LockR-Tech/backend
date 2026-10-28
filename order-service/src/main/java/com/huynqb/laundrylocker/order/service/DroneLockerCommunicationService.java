package com.huynqb.laundrylocker.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.dto.BoxCellDto;
import com.huynqb.laundrylocker.order.model.DroneDeliverySession;
import com.huynqb.laundrylocker.order.model.DroneLockerCommLog;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneDeliverySessionRepository;
import com.huynqb.laundrylocker.order.repository.DroneLockerCommLogRepository;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Kênh MQTT giữa drone, tủ và backend cho chặng cuối: drone nhận diện mã ArUco của ô đích → tủ mở nắp →
 * drone nhả hàng → tủ đóng nắp → đơn sang "hàng đã vào ô".
 *
 * <p>Nguyên tắc an toàn (QA audit 24/10/2026):
 * <ul>
 *   <li><b>Sự kiện drone phải có chữ ký</b> của đúng drone (HMAC theo {@link DroneTelemetrySigner}), còn
 *       mới (lệch giờ ≤ {@code max-clock-skew-seconds}) và chưa từng nhận (chống phát lại theo messageId).
 *       Lệnh gửi xuống drone cũng được ký để Pi chỉ làm theo backend thật.</li>
 *   <li><b>Định danh theo drone đã xác thực</b>: sự kiện gắn vào nhiệm vụ đang bay của chính drone đó; orderId /
 *       lockerId trong bản tin (nếu có) phải khớp, không bao giờ "đoán" đơn.</li>
 *   <li><b>Kết quả nắp của tủ</b> chỉ được chấp nhận khi khớp đúng mã lệnh mở/đóng server đã gửi cho phiên
 *       đang chờ, cùng tủ, cùng ô; trùng lặp hoặc sai thứ tự thì bỏ qua.</li>
 *   <li><b>Phiên giao lưu database</b> ({@link DroneDeliverySession}), không mất khi khởi động lại.</li>
 *   <li>Mọi chuyển chặng đi qua {@link DroneMissionProgressService#assertForwardTransition}: đơn đã huỷ / kết
 *       thúc không thể bị mở lại.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroneLockerCommunicationService {

    public static final String DRONE_EVENTS_TOPIC = "lockr/drone/delivery/events";
    public static final String DRONE_COMMANDS_TOPIC = "lockr/drone/delivery/commands";
    public static final String LOCKER_RESULT_SUBSCRIPTION = "cabinet/+/command/+/result";
    public static final String LOCKER_HEARTBEAT_SUBSCRIPTION = "cabinet/+/heartbeat";
    static final String SIGNATURE_PROPERTY = "sig";

    private final DroneLockerCommLogRepository commLogRepository;
    private final LockerOrderRepository orderRepository;
    private final DroneMissionRepository missionRepository;
    private final DroneMissionProgressService progressService;
    private final LockerDroneClient lockerDroneClient;
    private final OrderRules orderRules;
    private final ObjectMapper objectMapper;
    private final DroneDeliverySessionRepository sessionRepository;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.drone.telemetry.broker-url:tcp://broker.hivemq.com:1883}")
    private String brokerUrl;

    @Value("${app.drone.telemetry.username:}")
    private String username;

    @Value("${app.drone.telemetry.password:}")
    private String password;

    /// Khoá gốc dùng chung với telemetry: khoá từng drone = HMAC(khoá gốc, "lockr-drone:" + droneId).
    @Value("${app.drone.telemetry.secret:}")
    private String secret;

    @Value("${app.drone.telemetry.max-clock-skew-seconds:120}")
    private long maxClockSkewSeconds = 120;

    /// Tắt chỉ cho môi trường dev dùng broker riêng; production luôn bắt buộc chữ ký.
    @Value("${app.drone.delivery.require-signature:true}")
    private boolean requireSignature = true;

    // Thời gian chờ / số lần gửi lại lệnh nắp và ngưỡng coi tủ mất kết nối đọc từ OrderRules
    // (admin chỉnh ở nhóm "Kết nối tủ & định vị drone"). Pi gửi heartbeat mỗi 60 s; lệnh gửi lúc tủ offline
    // bị broker bỏ vì Pi dùng clean session, nên tủ offline thì chờ tủ quay lại thay vì đốt lượt gửi lại.

    /// Chặng bay được phép mở nắp: đang tới / đang tiếp cận / đã tới tủ nhận. Lúc cất cánh ở tủ gửi thì không.
    static final List<String> LID_ALLOWED_STAGES = List.of("EN_ROUTE", "APPROACHING", "ARRIVED");

    /// Pi tủ (code nắp drone) gửi kèm kết quả mở: LIMIT_SWITCH = công tắc hành trình xác nhận, STEP_COUNT = chạy
    /// hết số bước. BYPASS (giả lập) hoặc thiếu trường này thì không phải nắp trượt thật.
    static final Set<String> HARDWARE_CONFIRMATIONS = Set.of("LIMIT_SWITCH", "STEP_COUNT", "BYPASS");

    private MqttClient mqttClient;

    /// Lần gửi gần nhất của lệnh nắp đang chờ, theo commandId (mất khi restart ⇒ lấy updatedAt của phiên).
    /// `acked`: tủ đã trả ACCEPTED cho lệnh này (đang chạy nắp).
    private record LidCommandAttempt(LocalDateTime sentAt, int retry, boolean acked) {
    }

    private final Map<String, LidCommandAttempt> lidCommandAttempts = new ConcurrentHashMap<>();

    /// Lần gần nhất thấy tủ còn sống (heartbeat hoặc kết quả lệnh), theo lockerId.
    private final Map<Long, LocalDateTime> lockerSeenAt = new ConcurrentHashMap<>();

    /// Lệnh mở đã nhận một kết quả "đã mở" không có xác nhận phần cứng — không bao giờ gửi lại.
    private final Set<String> unverifiedOpenCommands = ConcurrentHashMap.newKeySet();

    /// Tủ đã từng trả ACCEPTED ⇒ chạy code nắp drone mới (nhớ commandId, không chạy motor hai lần) ⇒ gửi lại
    /// nhanh khi mất ACK là an toàn. Tủ chưa chứng minh được thì chỉ gửi lại sau thời gian chạy nắp đầy đủ.
    private final Set<Long> ackCapableLockers = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread t = new Thread(runnable, "drone-locker-scheduler");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService eventExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread t = new Thread(runnable, "drone-locker-events");
        t.setDaemon(true);
        return t;
    });

    /// Bản xem phiên giao cho trang admin (giữ nguyên dạng dữ liệu cũ của endpoint active-sessions).
    @Getter
    @Setter
    public static class DeliverySessionState {
        private String sessionId;
        private String correlationId;
        private Long orderId;
        private Long journeyId;
        private Long droneUnitId;
        private String droneId;
        private Long lockerId;
        private Long compartmentId;
        private String targetDictionary;
        private Integer targetMarkerId;
        private String mode; // "INDOOR_ARUCO_DEMO" or "REAL_OUTDOOR"
        private String state;
        private String openCommandId;
        private String closeCommandId;
        private LocalDateTime lidOpenedAt;
        private LocalDateTime createdAt;

        static DeliverySessionState of(DroneDeliverySession session) {
            DeliverySessionState view = new DeliverySessionState();
            view.setSessionId(session.getSessionId());
            view.setCorrelationId(session.getCorrelationId());
            view.setOrderId(session.getOrderId());
            view.setJourneyId(session.getMissionId());
            view.setDroneUnitId(session.getDroneUnitId());
            view.setDroneId(session.getDroneId());
            view.setLockerId(session.getLockerId());
            view.setCompartmentId(session.getCompartmentId());
            view.setTargetDictionary(session.getTargetDictionary());
            view.setTargetMarkerId(session.getTargetMarkerId());
            view.setMode(session.getMode());
            view.setState(session.getState());
            view.setOpenCommandId(session.getOpenCommandId());
            view.setCloseCommandId(session.getCloseCommandId());
            view.setLidOpenedAt(session.getLidOpenedAt());
            view.setCreatedAt(session.getCreatedAt());
            return view;
        }
    }

    @PostConstruct
    public void init() {
        if (requireSignature && !StringUtils.hasText(secret)) {
            log.warn("[COMM] DRONE_TELEMETRY_SECRET chưa đặt: mọi sự kiện drone sẽ bị từ chối (cần chữ ký)");
        }
        try {
            String clientId = "lockr-order-comm-" + UUID.randomUUID().toString().substring(0, 8);
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setAutomaticReconnect(true);
            options.setCleanStart(true);
            if (StringUtils.hasText(username)) {
                options.setUserName(username);
                options.setPassword(password.getBytes(StandardCharsets.UTF_8));
            }

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void disconnected(MqttDisconnectResponse disconnectResponse) {
                    log.warn("[COMM] MQTT disconnected: {}", disconnectResponse.getReasonString());
                }

                @Override
                public void mqttErrorOccurred(MqttException exception) {
                    log.warn("[COMM] MQTT error: {}", exception.getMessage());
                }

                @Override
                public void deliveryComplete(IMqttToken token) {
                }

                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("[COMM] MQTT connected (reconnect={}) to {}", reconnect, serverURI);
                    subscribeTopics();
                }

                @Override
                public void authPacketArrived(int reasonCode, MqttProperties properties) {
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    String signature = signatureOf(message);
                    eventExecutor.execute(() -> handleIncomingMessage(topic, payload, signature));
                }
            });

            connectOrRetry(options);
        } catch (MqttException e) {
            log.error("[COMM] Failed to create MQTT client for {}", brokerUrl, e);
        }
        // Chạy trên luồng sự kiện để không đua với kết quả nắp đang tới.
        scheduler.scheduleWithFixedDelay(() -> eventExecutor.execute(this::expireStaleOpenRequests),
                30, 30, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(() -> eventExecutor.execute(this::retryStalledLidCommands),
                5, 5, TimeUnit.SECONDS);
    }

    private void connectOrRetry(MqttConnectionOptions options) {
        try {
            mqttClient.connect(options);
            log.info("[COMM] Connected to MQTT broker: {}", brokerUrl);
            subscribeTopics();
        } catch (MqttException e) {
            log.error("[COMM] Connect failed ({}). Retry in 10s...", e.getMessage());
            scheduler.schedule(() -> connectOrRetry(options), 10, TimeUnit.SECONDS);
        }
    }

    private void subscribeTopics() {
        try {
            String[] topics = new String[]{DRONE_EVENTS_TOPIC, LOCKER_RESULT_SUBSCRIPTION, LOCKER_HEARTBEAT_SUBSCRIPTION};
            int[] qos = new int[]{1, 1, 0};
            mqttClient.subscribe(topics, qos);
            log.info("[COMM] Subscribed to topics: {}", Arrays.toString(topics));
        } catch (MqttException e) {
            log.error("[COMM] Subscribe error: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        scheduler.shutdownNow();
        eventExecutor.shutdownNow();
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
            } catch (Exception ignored) {
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

    // =========================================================================
    // MQTT INCOMING MESSAGE ROUTING
    // =========================================================================

    /// Bản tin không có chữ ký (dùng cho test / công cụ chẩn đoán).
    public void handleIncomingMessage(String topic, String payload) {
        handleIncomingMessage(topic, payload, null);
    }

    public void handleIncomingMessage(String topic, String payload, String signature) {
        if (topic.startsWith("cabinet/") && topic.endsWith("/heartbeat")) {
            String[] parts = topic.split("/");
            Long lockerId = parts.length == 3 ? parseLongOrNull(parts[1]) : null;
            boolean reconnected = false;
            if (lockerId != null) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime previous = lockerSeenAt.put(lockerId, now);
                // Trước đó đã thấy tủ rồi mất liên lạc quá ngưỡng ⇒ lệnh gửi trong lúc đó bị broker bỏ (clean
                // session), tủ chắc chắn chưa chạy ⇒ gửi lại ngay là an toàn kể cả với code tủ cũ.
                reconnected = previous != null && !previous.plusSeconds(orderRules.lockerOfflineAfterSeconds()).isAfter(now);
            }
            try {
                resendPendingLidCommands(lockerId, reconnected);
            } catch (Exception e) {
                log.error("[COMM] Error resending lid commands on {}: {}", topic, e.getMessage(), e);
            }
            return;
        }
        log.info("[COMM] Incoming MQTT [{}] -> {}", topic, payload);
        try {
            JsonNode data = objectMapper.readTree(payload);
            if (DRONE_EVENTS_TOPIC.equals(topic)) {
                handleDroneEvent(topic, data, payload, signature);
            } else if (topic.startsWith("cabinet/") && topic.endsWith("/result")) {
                handleLockerResult(topic, data, payload);
            }
        } catch (Exception e) {
            log.error("[COMM] Error handling message on topic {}: {}", topic, e.getMessage(), e);
        }
    }

    // =========================================================================
    // 1. DRONE EVENTS (Raspberry Pi -> Backend)
    // =========================================================================

    private void handleDroneEvent(String topic, JsonNode data, String rawPayload, String signature) {
        String eventType = text(data, "eventType");
        String messageId = text(data, "messageId");
        String correlationId = text(data, "correlationId");
        String sessionId = text(data, "sessionId");
        Long orderId = longOrNull(data, "orderId");
        String droneId = text(data, "droneId");
        Long lockerId = longOrNull(data, "lockerId");
        Long compartmentId = longOrNull(data, "compartmentId");
        String dictionary = text(data, "arucoDictionary");
        Integer markerId = intOrNull(data, "arucoMarkerId");
        String mode = text(data, "mode");
        if (mode == null) {
            mode = orderRules.droneDemoIndoorArucoEnabled() ? "INDOOR_ARUCO_DEMO" : "REAL_OUTDOOR";
        }

        String rejection = rejectionOf(topic, data, rawPayload, signature, droneId, messageId);
        DroneLockerCommLog inLog = saveLog(
                messageId, correlationId, sessionId, orderId, null, droneId, lockerId, compartmentId,
                markerId, dictionary, "DRONE", "BACKEND", DRONE_EVENTS_TOPIC, eventType,
                "INBOUND", rawPayload, "DELIVERED", "RECEIVED", rejection == null ? "PROCESSING" : "REJECTED",
                rejection, rejection == null ? null : rejectionMessage(rejection), 0, null);
        if (rejection != null) {
            log.warn("[COMM] Drone event {} from {} rejected: {}", eventType, droneId, rejection);
            return;
        }
        if (inLog == null) {
            inLog = new DroneLockerCommLog();
        }

        if ("MARKER_DETECTED".equalsIgnoreCase(eventType)) {
            processMarkerDetected(inLog, droneId, orderId, lockerId, markerId, dictionary, sessionId,
                    correlationId, mode);
        } else if ("DROP_PHASE_COMPLETED".equalsIgnoreCase(eventType)) {
            processDropPhaseCompleted(inLog, droneId, sessionId);
        } else if ("SAFETY_ABORT".equalsIgnoreCase(eventType)) {
            processSafetyAbort(inLog, droneId);
        } else if ("TARGET_REQUEST".equalsIgnoreCase(eventType)) {
            processTargetRequest(inLog, droneId);
        } else {
            finish(inLog, "REJECTED", "UNKNOWN_EVENT", "Loại sự kiện không hỗ trợ: " + eventType);
        }
    }

    /// Lý do từ chối một sự kiện drone, hoặc null nếu hợp lệ: có droneId, chữ ký đúng, còn mới, chưa từng nhận.
    String rejectionOf(String topic, JsonNode data, String rawPayload, String signature, String droneId,
            String messageId) {
        if (!StringUtils.hasText(droneId)) {
            return "DRONE_ID_MISSING";
        }
        if (requireSignature) {
            if (!StringUtils.hasText(secret)) {
                return "SIGNATURE_NOT_CONFIGURED";
            }
            if (!DroneTelemetrySigner.verify(secret, droneId, topic, rawPayload, signature)) {
                return "BAD_SIGNATURE";
            }
        }
        Instant sentAt = instantOrNull(text(data, "timestamp"));
        if (sentAt == null || Math.abs(Instant.now().toEpochMilli() - sentAt.toEpochMilli()) > maxClockSkewSeconds * 1000) {
            return "STALE_EVENT";
        }
        if (!StringUtils.hasText(messageId)) {
            return "MESSAGE_ID_MISSING";
        }
        if (commLogRepository.existsByMessageIdAndDirection(messageId, "INBOUND")) {
            return "DUPLICATE_EVENT";
        }
        return null;
    }

    private static String rejectionMessage(String code) {
        return switch (code) {
            case "DRONE_ID_MISSING" -> "Sự kiện thiếu droneId";
            case "SIGNATURE_NOT_CONFIGURED" -> "Backend chưa cấu hình khoá ký sự kiện drone";
            case "BAD_SIGNATURE" -> "Chữ ký sự kiện sai hoặc thiếu";
            case "STALE_EVENT" -> "Thời điểm gửi thiếu hoặc lệch quá xa giờ server (phát lại / sai đồng hồ)";
            case "MESSAGE_ID_MISSING" -> "Sự kiện thiếu messageId";
            case "DUPLICATE_EVENT" -> "Sự kiện đã được nhận trước đó";
            default -> code;
        };
    }

    /// Kết quả xử lý trong giao dịch: lệnh cần gửi ra MQTT SAU khi dữ liệu đã lưu.
    private record Outbound(DroneDeliverySession session, String droneCommand, boolean openLid) {
    }

    void processMarkerDetected(DroneLockerCommLog inLog, String droneId, Long claimedOrderId, Long claimedLockerId,
            Integer detectedMarkerId, String detectedDict, String eventSessionId, String eventCorrelationId,
            String mode) {
        String dict = detectedDict != null ? detectedDict : "DICT_4X4_50";
        Outbound outbound;
        try {
            outbound = transactions().execute(status -> {
                // Định danh theo drone đã ký: nhiệm vụ đang bay của chính drone này — không đoán đơn.
                DroneMission mission = missionRepository
                        .findFirstByDroneCodeAndStatusInOrderByIdDesc(droneId, DroneMissionProgressService.IN_FLIGHT_STAGES)
                        .orElseGet(() -> indoorDemoMission(droneId, claimedOrderId, claimedLockerId, detectedMarkerId, dict, mode));
                if (mission == null) {
                    throw reject("NO_ACTIVE_MISSION", "Drone " + droneId + " không có nhiệm vụ đang bay");
                }
                if (!LID_ALLOWED_STAGES.contains(mission.getStatus())) {
                    throw reject("STAGE_NOT_ALLOWED", "Nhiệm vụ #" + mission.getId() + " đang ở chặng "
                            + mission.getStatus() + " — chỉ mở nắp khi drone đang tới hoặc đã tới tủ nhận");
                }
                LockerOrder order = orderRepository.findByIdForUpdate(mission.getOrderId())
                        .orElseThrow(() -> reject("ORDER_NOT_FOUND", "Không tìm thấy đơn của nhiệm vụ #" + mission.getId()));
                if (claimedOrderId != null && !claimedOrderId.equals(order.getId())) {
                    throw reject("ORDER_MISMATCH", "Sự kiện nói đơn #" + claimedOrderId + " nhưng drone đang chở đơn #"
                            + order.getId());
                }
                Long destLockerId = order.getLockerId();
                if (claimedLockerId != null && !claimedLockerId.equals(destLockerId)) {
                    throw reject("LOCKER_MISMATCH", "Sự kiện nói tủ #" + claimedLockerId + " nhưng tủ nhận là #" + destLockerId);
                }
                if (!"AWAITING_DISPATCH".equals(order.getStatus())) {
                    throw reject("ORDER_NOT_IN_DELIVERY", "Đơn #" + order.getId() + " không còn đang giao (" + order.getStatus() + ")");
                }
                inLog.setOrderId(order.getId());
                inLog.setJourneyId(mission.getId());
                inLog.setLockerId(destLockerId);

                // Drone gửi lặp khi đang chờ nắp: giữ nguyên phiên, không mở nắp lần nữa.
                Optional<DroneDeliverySession> active = sessionRepository.findFirstByOrderIdAndStateInOrderByCreatedAtDesc(
                        order.getId(), DroneDeliverySession.ACTIVE_STATES);
                if (active.isPresent()) {
                    inLog.setSessionId(active.get().getSessionId());
                    finish(inLog, "IGNORED", "DUPLICATE_MARKER", "Đơn đã có phiên giao đang chạy");
                    return new Outbound(active.get(), "MARKER_VALIDATED", false);
                }

                Long targetBoxId = order.getReservedBoxId() != null ? order.getReservedBoxId() : order.getSendBoxId();
                BoxCellDto targetCell = fetchCell(targetBoxId);
                Integer expectedMarkerId = targetCell != null ? targetCell.arucoMarkerId() : null;
                String expectedDict = targetCell != null && targetCell.arucoDictionary() != null
                        ? targetCell.arucoDictionary() : "DICT_4X4_50";
                if (expectedMarkerId == null || !expectedMarkerId.equals(detectedMarkerId)
                        || !expectedDict.equalsIgnoreCase(dict)) {
                    String errMsg = String.format("Mã ArUco phát hiện (#%s, %s) không khớp với ô đích #%s (cần mã #%s, %s)",
                            detectedMarkerId, dict, targetBoxId, expectedMarkerId, expectedDict);
                    recordDroneIotLog(mission.getDroneUnitId(), droneId, "VISION_CAMERA", "MARKER_MISMATCH", "FAILED",
                            order.getId(), errMsg);
                    throw reject("WRONG_MARKER", errMsg);
                }

                DroneDeliverySession session = new DroneDeliverySession();
                session.setSessionId(StringUtils.hasText(eventSessionId) ? eventSessionId
                        : "SESSION-" + order.getId() + "-" + System.currentTimeMillis());
                session.setCorrelationId(StringUtils.hasText(eventCorrelationId) ? eventCorrelationId
                        : UUID.randomUUID().toString());
                session.setOrderId(order.getId());
                session.setMissionId(mission.getId());
                session.setDroneId(droneId);
                session.setDroneUnitId(mission.getDroneUnitId());
                session.setLockerId(destLockerId);
                session.setCompartmentId(targetBoxId);
                session.setTargetMarkerId(expectedMarkerId);
                session.setTargetDictionary(expectedDict);
                session.setMode(mode);
                session.setOpenCommandId(UUID.randomUUID().toString());
                // Gán ngay: hạn lệnh mở tính từ đây và phải giữ nguyên qua mọi lần gửi lại.
                session.setCreatedAt(LocalDateTime.now());
                session.setState(DroneDeliverySession.OPEN_LID_REQUESTED);
                sessionRepository.save(session);

                // Camera thấy đúng mã ô đích ⇒ drone đã tới tủ nhận (chỉ đi tới, không lùi chặng).
                if (DroneMissionProgressService.FLIGHT_ORDER.indexOf(mission.getStatus())
                        < DroneMissionProgressService.FLIGHT_ORDER.indexOf("ARRIVED")) {
                    progressService.advanceToStage(order, mission, "ARRIVED", null,
                            "Camera drone nhận diện đúng mã ArUco #" + expectedMarkerId + " của ô tủ");
                }
                inLog.setSessionId(session.getSessionId());
                finish(inLog, "SUCCESS", null, "Xác thực mã ArUco hợp lệ");
                recordDroneIotLog(mission.getDroneUnitId(), droneId, "VISION_CAMERA", "DETECT_TARGET", "SUCCESS",
                        order.getId(), "Camera nhận diện mã ArUco #" + detectedMarkerId + " (" + dict + ")");
                return new Outbound(session, "MARKER_VALIDATED", true);
            });
        } catch (Rejected rejected) {
            finish(inLog, "REJECTED", rejected.code, rejected.getMessage());
            log.warn("[COMM] MARKER_DETECTED from {} rejected: {} {}", droneId, rejected.code, rejected.getMessage());
            // Thấy mã của ô khác: drone tiếp tục tìm đúng ô; còn lại (không có nhiệm vụ, sai đơn/tủ…) thì dừng.
            publishCommand("WRONG_MARKER".equals(rejected.code) ? "MARKER_REJECTED" : "SAFETY_ABORT",
                    eventSessionId, eventCorrelationId, claimedOrderId, droneId,
                    claimedLockerId, null, rejected.code, rejected.getMessage());
            return;
        } catch (BusinessException transition) {
            finish(inLog, "REJECTED", transition.getCode(), transition.getMessage());
            log.warn("[COMM] MARKER_DETECTED from {} rejected: {}", droneId, transition.getMessage());
            return;
        }
        if (outbound == null) {
            return;
        }
        DroneDeliverySession session = outbound.session();
        publishCommand(outbound.droneCommand(), session.getSessionId(), session.getCorrelationId(), session.getOrderId(),
                droneId, session.getLockerId(), session.getCompartmentId(), null, null);
        if (outbound.openLid()) {
            sendLockerLidCommand(session.getLockerId(), session.getCompartmentId(), "OPEN", session.getOpenCommandId(),
                    openCommandExpiresAt(session));
            lidCommandAttempts.put(session.getOpenCommandId(), new LidCommandAttempt(LocalDateTime.now(), 0, false));
            saveLog(session.getOpenCommandId(), session.getCorrelationId(), session.getSessionId(),
                    session.getOrderId(), session.getMissionId(), droneId, session.getLockerId(), session.getCompartmentId(),
                    session.getTargetMarkerId(), session.getTargetDictionary(), "BACKEND", "LOCKER",
                    "cabinet/" + session.getLockerId() + "/command/open", "OPEN_LID_REQUESTED", "OUTBOUND", null,
                    "SENT", "PENDING", "PENDING", null, "Đã gửi lệnh mở nắp trượt ô tủ (mã ArUco #"
                            + session.getTargetMarkerId() + ", hạn " + openCommandExpiresAt(session) + ")", 0, null);
            log.info("[COMM] OPEN_LID sent: mission={} order={} locker={} box={} aruco={} commandId={} expiresAt={}",
                    session.getMissionId(), session.getOrderId(), session.getLockerId(), session.getCompartmentId(),
                    session.getTargetMarkerId(), session.getOpenCommandId(), openCommandExpiresAt(session));
        }
    }

    // =========================================================================
    // 1b. MỤC TIÊU GIAO HÀNG CHO PI DRONE (Pi hỏi → Backend trả)
    // =========================================================================

    /// Mục tiêu Pi drone cần biết để chỉ nhận đúng mã ArUco của ô đích.
    record DeliveryTarget(Long missionId, Long orderId, Long lockerId, Long boxId, Integer markerId,
            String dictionary, String mode, String stage, String sessionState) {
    }

    /// Pi drone hỏi "đang giao tới ô nào" (lúc khởi động, kết nối lại và định kỳ). Chỉ trả mục tiêu khi drone có
    /// nhiệm vụ ở chặng được phép mở nắp, hoặc demo trong nhà có ĐÚNG MỘT đơn DEMO chờ giao. Mã ArUco lấy từ cấu
    /// hình ô đích ở locker-service — Pi không tự đoán, không có mã cứng trong code.
    void processTargetRequest(DroneLockerCommLog inLog, String droneId) {
        String[] reason = {null};
        DeliveryTarget target;
        try {
            target = transactions().execute(status -> resolveTarget(droneId, reason));
        } catch (RuntimeException ex) {
            target = null;
            reason[0] = "Không tra được nhiệm vụ: " + ex.getMessage();
        }
        if (target == null) {
            finish(inLog, "SUCCESS", "NO_TARGET", reason[0]);
            publishCommand("NO_TARGET", null, null, null, droneId, null, null, "NO_TARGET", reason[0], Map.of());
            return;
        }
        inLog.setOrderId(target.orderId());
        inLog.setJourneyId(target.missionId());
        inLog.setLockerId(target.lockerId());
        finish(inLog, "SUCCESS", null, "Gửi mục tiêu: tủ #" + target.lockerId() + ", ô #" + target.boxId()
                + ", mã ArUco #" + target.markerId());
        Map<String, Object> extra = new LinkedHashMap<>();
        if (target.missionId() != null) extra.put("missionId", target.missionId());
        extra.put("arucoMarkerId", target.markerId());
        extra.put("arucoDictionary", target.dictionary());
        extra.put("mode", target.mode());
        extra.put("stage", target.stage());
        if (target.sessionState() != null) extra.put("sessionState", target.sessionState());
        publishCommand("DELIVERY_TARGET", null, null, target.orderId(), droneId, target.lockerId(), target.boxId(),
                null, null, extra);
    }

    private DeliveryTarget resolveTarget(String droneId, String[] reason) {
        DroneMission mission = missionRepository
                .findFirstByDroneCodeAndStatusInOrderByIdDesc(droneId, LID_ALLOWED_STAGES)
                .filter(found -> LID_ALLOWED_STAGES.contains(found.getStatus()))
                .orElse(null);
        LockerOrder order = null;
        String mode;
        if (mission != null) {
            order = orderRepository.findById(mission.getOrderId()).orElse(null);
            mode = order != null && "DEMO".equalsIgnoreCase(order.getFulfillmentMode())
                    && orderRules.droneDemoIndoorArucoEnabled() ? "INDOOR_ARUCO_DEMO" : "REAL_OUTDOOR";
        } else if (orderRules.droneDemoIndoorHandheldEnabled()) {
            List<LockerOrder> demo = orderRepository
                    .findByTypeAndStatusNotInOrderByUpdatedAtDesc("DRONE_DELIVERY", CLOSED_ORDER_STATUSES).stream()
                    .filter(candidate -> "AWAITING_DISPATCH".equals(candidate.getStatus()))
                    .filter(candidate -> "DEMO".equalsIgnoreCase(candidate.getFulfillmentMode()))
                    .filter(candidate -> missionRepository.findByOrderId(candidate.getId()).isEmpty())
                    .limit(2)
                    .toList();
            if (demo.size() != 1) {
                reason[0] = demo.isEmpty() ? "Drone " + droneId + " chưa có nhiệm vụ và không có đơn DEMO chờ giao"
                        : "Có nhiều đơn DEMO chờ giao — không tự chọn, cần KTV phóng đúng nhiệm vụ";
                return null;
            }
            order = demo.getFirst();
            mode = "INDOOR_ARUCO_DEMO";
        } else {
            reason[0] = "Drone " + droneId + " chưa có nhiệm vụ ở chặng được phép mở nắp (" + LID_ALLOWED_STAGES + ")";
            return null;
        }
        if (order == null || !"AWAITING_DISPATCH".equals(order.getStatus())) {
            reason[0] = "Đơn của nhiệm vụ không còn đang giao";
            return null;
        }
        Long boxId = order.getReservedBoxId() != null ? order.getReservedBoxId()
                : order.getReceiveBoxId() != null ? order.getReceiveBoxId() : order.getSendBoxId();
        BoxCellDto cell = fetchCell(boxId);
        if (cell == null || cell.arucoMarkerId() == null) {
            reason[0] = "Ô nhận #" + boxId + " chưa được gán mã ArUco";
            return null;
        }
        String sessionState = sessionRepository.findFirstByOrderIdAndStateInOrderByCreatedAtDesc(order.getId(),
                DroneDeliverySession.ACTIVE_STATES).map(DroneDeliverySession::getState).orElse(null);
        return new DeliveryTarget(mission != null ? mission.getId() : null, order.getId(), order.getLockerId(), boxId,
                cell.arucoMarkerId(), cell.arucoDictionary() != null ? cell.arucoDictionary() : "DICT_4X4_50", mode,
                mission != null ? mission.getStatus() : order.getDeliveryStage(), sessionState);
    }

    /// Đơn đã kết thúc hoặc đã vào ô: không còn ứng viên cho demo.
    private static final List<String> CLOSED_ORDER_STATUSES =
            List.of("COMPLETED", "CANCELED", "EXPIRED", "STORING", "DELIVERY_INCOMPLETE");

    /**
     * Demo ArUco trong nhà: drone cầm tay đưa tới ô mà chưa có KTV phóng (chưa có nhiệm vụ bay). Chỉ khi admin
     * bật chế độ này, sự kiện đã ký, và có ĐÚNG MỘT đơn DEMO đang giao mà ô nhận mang đúng mã ArUco vừa thấy
     * (khớp orderId / lockerId nếu bản tin có ghi) — không bao giờ lấy đại đơn đầu tiên. Tạo nhiệm vụ ở chặng
     * APPROACHING cho chính drone đã ký; phần còn lại đi đúng luồng có kiểm như nhiệm vụ thật.
     */
    private DroneMission indoorDemoMission(String droneId, Long claimedOrderId, Long claimedLockerId,
            Integer detectedMarkerId, String detectedDict, String mode) {
        if (!"INDOOR_ARUCO_DEMO".equalsIgnoreCase(mode) || !orderRules.droneDemoIndoorHandheldEnabled()
                || detectedMarkerId == null) {
            return null;
        }
        List<LockerOrder> matches = orderRepository
                .findByTypeAndStatusNotInOrderByUpdatedAtDesc("DRONE_DELIVERY", CLOSED_ORDER_STATUSES).stream()
                .filter(candidate -> "AWAITING_DISPATCH".equals(candidate.getStatus()))
                .filter(candidate -> "DEMO".equalsIgnoreCase(candidate.getFulfillmentMode()))
                .filter(candidate -> claimedOrderId == null || claimedOrderId.equals(candidate.getId()))
                .filter(candidate -> claimedLockerId == null || claimedLockerId.equals(candidate.getLockerId()))
                .filter(candidate -> missionRepository.findByOrderId(candidate.getId()).isEmpty())
                .filter(candidate -> {
                    Long boxId = candidate.getReservedBoxId() != null ? candidate.getReservedBoxId() : candidate.getSendBoxId();
                    BoxCellDto cell = fetchCell(boxId);
                    String dict = cell == null || cell.arucoDictionary() == null ? "DICT_4X4_50" : cell.arucoDictionary();
                    return cell != null && detectedMarkerId.equals(cell.arucoMarkerId()) && dict.equalsIgnoreCase(detectedDict);
                })
                .limit(2)
                .toList();
        if (matches.size() != 1) {
            log.warn("[COMM] Indoor demo marker #{} from {} matches {} DEMO orders — not bound",
                    detectedMarkerId, droneId, matches.size());
            return null;
        }
        LockerOrder order = orderRepository.findByIdForUpdate(matches.getFirst().getId()).orElse(null);
        if (order == null || !"AWAITING_DISPATCH".equals(order.getStatus())) {
            return null;
        }
        DroneMission mission = new DroneMission();
        mission.setOrderId(order.getId());
        mission.setDroneCode(droneId);
        mission.setSourceLockerId(order.getSourceLockerId() != null ? order.getSourceLockerId() : order.getLockerId());
        mission.setDestinationLockerId(order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId());
        mission.setStatus("APPROACHING");
        mission.setLaunchingAt(LocalDateTime.now());
        mission = missionRepository.save(mission);
        // Chặng của đơn phải khớp nhiệm vụ để các bước sau qua được kiểm tra chuyển chặng.
        order.setDeliveryStage("APPROACHING");
        orderRepository.save(order);
        log.info("[COMM] Indoor demo: created mission #{} for DEMO order #{} on drone {}", mission.getId(), order.getId(), droneId);
        return mission;
    }

    /// Drone báo đã nhả hàng: chỉ chấp nhận cho phiên của chính drone đó đang mở nắp chờ thả.
    void processDropPhaseCompleted(DroneLockerCommLog inLog, String droneId, String eventSessionId) {
        DroneDeliverySession session = sessionRepository
                .findFirstByDroneIdAndStateOrderByCreatedAtDesc(droneId, DroneDeliverySession.LID_OPENED)
                .orElse(null);
        if (session == null) {
            finish(inLog, "REJECTED", "NO_SESSION_WAITING_DROP", "Drone " + droneId + " không có phiên nào đang mở nắp chờ thả");
            return;
        }
        if (StringUtils.hasText(eventSessionId) && !eventSessionId.equals(session.getSessionId())) {
            finish(inLog, "REJECTED", "SESSION_MISMATCH", "Phiên " + eventSessionId + " không phải phiên đang chờ thả");
            return;
        }
        inLog.setSessionId(session.getSessionId());
        inLog.setOrderId(session.getOrderId());
        finish(inLog, "SUCCESS", null, "Drone xác nhận đã nhả hàng");
        recordDroneIotLog(session.getDroneUnitId(), droneId, "CARGO_HOOK", "HOOK_RELEASE", "RELEASED",
                session.getOrderId(), "Móc treo nhả kiện hàng vào ô tủ");
        requestCloseLid(session, "Drone xác nhận hoàn tất giai đoạn nhả hàng");
    }

    private void processSafetyAbort(DroneLockerCommLog inLog, String droneId) {
        finish(inLog, "ABORTED", null, "Drone " + droneId + " huỷ thao tác an toàn");
        log.warn("[COMM] Safety abort from drone {}: {}", droneId, inLog.getPayload());
    }

    private void requestCloseLid(DroneDeliverySession session, String reason) {
        session.setCloseCommandId(UUID.randomUUID().toString());
        session.setState(DroneDeliverySession.CLOSE_LID_REQUESTED);
        sessionRepository.save(session);
        sendLockerLidCommand(session.getLockerId(), session.getCompartmentId(), "CLOSE", session.getCloseCommandId(), null);
        lidCommandAttempts.put(session.getCloseCommandId(), new LidCommandAttempt(LocalDateTime.now(), 0, false));
        saveLog(session.getCloseCommandId(), session.getCorrelationId(), session.getSessionId(),
                session.getOrderId(), session.getMissionId(), session.getDroneId(), session.getLockerId(),
                session.getCompartmentId(), session.getTargetMarkerId(), session.getTargetDictionary(),
                "BACKEND", "LOCKER", "cabinet/" + session.getLockerId() + "/command/close",
                "CLOSE_LID_REQUESTED", "OUTBOUND", null, "SENT", "PENDING", "PENDING", null, reason, 0, null);
    }

    // =========================================================================
    // 2. LOCKER RESULTS (Locker -> Backend)
    // =========================================================================

    /// Chỉ kết quả trả lời đúng lệnh mở/đóng nắp server đã gửi cho phiên đang chờ mới được xử lý.
    /// Kết quả của lệnh khác (kiosk mở ô cho khách, iot-service…) không thuộc kênh này ⇒ bỏ qua.
    private void handleLockerResult(String topic, JsonNode data, String rawPayload) {
        String commandId = text(data, "commandId");
        String[] parts = topic.split("/");
        Long lockerId = parts.length >= 2 ? parseLongOrNull(parts[1]) : null;
        String action = parts.length >= 4 ? parts[3].toLowerCase(Locale.ROOT) : "unknown";
        if (!StringUtils.hasText(commandId) || !("open".equals(action) || "close".equals(action))) {
            return;
        }
        boolean open = "open".equals(action);
        Optional<DroneDeliverySession> found = open
                ? sessionRepository.findByOpenCommandId(commandId)
                : sessionRepository.findByCloseCommandId(commandId);
        if (found.isEmpty()) {
            log.debug("[COMM] Locker result for command {} is not a drone delivery command — ignored", commandId);
            return;
        }
        DroneDeliverySession session = found.get();
        if (lockerId != null) {
            lockerSeenAt.put(lockerId, LocalDateTime.now());
        }
        Long boxId = longOrNull(data, "boxId");
        String status = text(data, "status");
        boolean accepted = "ACCEPTED".equalsIgnoreCase(status);
        String hwState = text(data, "hwState");
        String errorCode = text(data, "errorCode");
        String errorMessage = text(data, "errorMessage");

        String mismatch = !Objects.equals(lockerId, session.getLockerId()) ? "LOCKER_MISMATCH"
                : boxId != null && !boxId.equals(session.getCompartmentId()) ? "BOX_MISMATCH"
                : !(open ? Set.of(DroneDeliverySession.OPEN_LID_REQUESTED, DroneDeliverySession.LID_OPENING)
                        : Set.of(DroneDeliverySession.CLOSE_LID_REQUESTED)).contains(session.getState()) ? "UNEXPECTED_STATE"
                : null;
        String eventType = open ? "LID_OPEN_RESULT" : "LID_CLOSE_RESULT";
        if (mismatch != null) {
            saveLog(commandId, session.getCorrelationId(), session.getSessionId(), session.getOrderId(),
                    session.getMissionId(), session.getDroneId(), lockerId, boxId, session.getTargetMarkerId(),
                    session.getTargetDictionary(), "LOCKER", "BACKEND", topic, eventType, "INBOUND", rawPayload,
                    "RECEIVED", "IGNORED", "IGNORED", mismatch,
                    "Kết quả nắp không khớp phiên (" + mismatch + ", phiên ở " + session.getState() + ")", 0, null);
            log.warn("[COMM] Locker result {} for session {} ignored: {}", commandId, session.getSessionId(), mismatch);
            return;
        }
        if (accepted) {
            handleLidAccepted(session, open, commandId, topic, rawPayload, data.has("duplicate")
                    && data.get("duplicate").asBoolean(false));
            return;
        }
        String lidConfirmation = text(data, "lidConfirmation");
        if (open && "SUCCESS".equalsIgnoreCase(status) && !HARDWARE_CONFIRMATIONS.contains(
                lidConfirmation == null ? "" : lidConfirmation.toUpperCase(Locale.ROOT))) {
            // Lệnh tới tủ không ký: "đã mở" mà không kèm xác nhận phần cứng có thể là trình giả lập
            // (simulate_demo_cabinet.py nghe mọi tủ trên broker chung) hoặc code tủ cũ. Không coi là nắp đã mở,
            // cũng không huỷ phiên — tiếp tục chờ kết quả thật của Pi tủ.
            saveLog(commandId, session.getCorrelationId(), session.getSessionId(), session.getOrderId(),
                    session.getMissionId(), session.getDroneId(), lockerId, boxId, session.getTargetMarkerId(),
                    session.getTargetDictionary(), "LOCKER", "BACKEND", topic, "LID_OPEN_RESULT", "INBOUND", rawPayload,
                    "RECEIVED", "IGNORED", "IGNORED", "LID_UNVERIFIED",
                    "Bỏ qua kết quả \"đã mở\" không có xác nhận phần cứng (lidConfirmation="
                            + (lidConfirmation == null ? "trống" : lidConfirmation)
                            + ") — có thể là trình giả lập tủ hoặc Pi tủ chưa cập nhật code; tiếp tục chờ tủ thật",
                    0, null);
            log.warn("[COMM] Ignored unverified OPEN result {} for session {} (lidConfirmation={}) — waiting for the real locker",
                    commandId, session.getSessionId(), lidConfirmation);
            // KHÔNG gửi lại lệnh này nữa: Pi tủ chạy code cũ không chống chạy trùng — gửi lại là motor chạy lại,
            // đẩy nắp đã ở cuối hành trình. Chờ hết thời gian chạy nắp; tủ thật (code mới) vẫn có thể trả lời.
            unverifiedOpenCommands.add(commandId);
            lidCommandAttempts.put(commandId, new LidCommandAttempt(LocalDateTime.now(), orderRules.lidMaxRetries(), true));
            return;
        }
        lidCommandAttempts.remove(commandId);
        if (open) {
            handleOpenResult(session, status, hwState, errorCode, errorMessage, topic, rawPayload, lidConfirmation);
        } else {
            handleCloseResult(session, status, hwState, errorCode, errorMessage, topic, rawPayload);
        }
    }

    /// Tủ báo ĐÃ NHẬN lệnh (ACCEPTED) và bắt đầu chạy nắp — chưa phải nắp đã mở/đóng xong. Từ đây không gửi lại
    /// theo hạn ACK nữa mà chờ theo thời gian chạy nắp.
    private void handleLidAccepted(DroneDeliverySession session, boolean open, String commandId, String topic,
            String rawPayload, boolean duplicate) {
        LidCommandAttempt last = lidCommandAttempts.get(commandId);
        lidCommandAttempts.put(commandId, new LidCommandAttempt(LocalDateTime.now(), last != null ? last.retry() : 0, true));
        ackCapableLockers.add(session.getLockerId());
        if (open && DroneDeliverySession.LID_OPENING.equals(session.getState())) {
            log.debug("[COMM] Duplicate ACCEPTED for open command {} — lid already moving", commandId);
            return;
        }
        saveLog(commandId, session.getCorrelationId(), session.getSessionId(), session.getOrderId(),
                session.getMissionId(), session.getDroneId(), session.getLockerId(), session.getCompartmentId(),
                session.getTargetMarkerId(), session.getTargetDictionary(), "LOCKER", "BACKEND", topic,
                open ? "LID_OPEN_ACCEPTED" : "LID_CLOSE_ACCEPTED", "INBOUND", rawPayload, "RECEIVED", "ACKNOWLEDGED",
                "PENDING", null, (open ? "Tủ đã nhận lệnh mở, nắp trượt đang chạy" : "Tủ đã nhận lệnh đóng, nắp trượt đang chạy")
                        + (duplicate ? " (lệnh gửi lại — tủ không chạy motor lần nữa)" : ""), 0, null);
        if (!open) {
            return;
        }
        session.setState(DroneDeliverySession.LID_OPENING);
        sessionRepository.save(session);
        publishCommand("LID_OPENING", session.getSessionId(), session.getCorrelationId(), session.getOrderId(),
                session.getDroneId(), session.getLockerId(), session.getCompartmentId(), null,
                "Tủ đã nhận lệnh, nắp trượt đang mở");
        log.info("[COMM] Locker {} accepted OPEN {} (session {}, order {}) — lid moving",
                session.getLockerId(), commandId, session.getSessionId(), session.getOrderId());
    }

    private void handleOpenResult(DroneDeliverySession session, String status, String hwState, String errorCode,
            String errorMessage, String topic, String rawPayload, String lidConfirmation) {
        boolean isSuccess = "SUCCESS".equalsIgnoreCase(status) && "OPEN".equalsIgnoreCase(hwState);
        // Luồng drone luôn mở nắp THẬT: Pi tủ bật LID_BYPASS_PHYSICAL_MOTION (chỉ báo, không trượt nắp) thì
        // không được coi là đã mở, kẻo hệ thống cho drone thả hàng vào ô còn đậy nắp.
        if (isSuccess && "BYPASS".equalsIgnoreCase(lidConfirmation)) {
            isSuccess = false;
            errorCode = "LID_SIMULATED";
            errorMessage = "Tủ đang bật LID_BYPASS_PHYSICAL_MOTION (giả lập, nắp không trượt) — tắt cờ này trên Pi tủ "
                    + "để mở nắp thật";
        }
        // Không có công tắc hành trình xác nhận: nắp đã chạy hết số bước nhưng không chắc đã mở hoàn toàn.
        boolean confirmedByLimit = "LIMIT_SWITCH".equalsIgnoreCase(lidConfirmation);
        String openedMessage = confirmedByLimit ? "Nắp ô tủ đã mở hoàn toàn"
                : "Nắp đã chạy hết hành trình theo số bước — tủ chưa có công tắc hành trình xác nhận mở hoàn toàn";
        saveLog(session.getOpenCommandId(), session.getCorrelationId(), session.getSessionId(), session.getOrderId(),
                session.getMissionId(), session.getDroneId(), session.getLockerId(), session.getCompartmentId(),
                session.getTargetMarkerId(), session.getTargetDictionary(), "LOCKER", "BACKEND", topic,
                isSuccess ? "LID_OPENED" : "LID_OPEN_FAILED", "INBOUND", rawPayload, "RECEIVED", "CONFIRMED",
                isSuccess ? "SUCCESS" : "FAILED", errorCode, isSuccess ? openedMessage : errorMessage, 0, null);
        log.info("[COMM] OPEN_LID result: mission={} order={} locker={} aruco={} commandId={} status={} hwState={} "
                        + "confirmation={} error={}", session.getMissionId(), session.getOrderId(), session.getLockerId(),
                session.getTargetMarkerId(), session.getOpenCommandId(), status, hwState, lidConfirmation, errorCode);
        if (!isSuccess) {
            session.setState(DroneDeliverySession.LID_OPEN_FAILED);
            session.setErrorCode(errorCode);
            session.setErrorMessage(errorMessage);
            sessionRepository.save(session);
            publishCommand("LID_OPEN_FAILED", session.getSessionId(), session.getCorrelationId(), session.getOrderId(),
                    session.getDroneId(), session.getLockerId(), session.getCompartmentId(), errorCode, errorMessage);
            return;
        }
        session.setState(DroneDeliverySession.LID_OPENED);
        session.setLidOpenedAt(LocalDateTime.now());
        sessionRepository.save(session);
        boolean demo = "INDOOR_ARUCO_DEMO".equalsIgnoreCase(session.getMode());
        int holdSeconds = orderRules.droneDemoHoldOpenSeconds() > 0 ? orderRules.droneDemoHoldOpenSeconds() : 7;
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("lidConfirmation", lidConfirmation != null ? lidConfirmation : "LIMIT_SWITCH");
        if (demo) {
            extra.put("dropSimulationSeconds", holdSeconds);
        }
        publishCommand("LID_OPENED", session.getSessionId(), session.getCorrelationId(), session.getOrderId(),
                session.getDroneId(), session.getLockerId(), session.getCompartmentId(), null, openedMessage, extra);
        recordDroneIotLog(session.getDroneUnitId(), session.getDroneId(), "CARGO_HOOK", "HOOK_ATTACH", "SUCCESS",
                session.getOrderId(), openedMessage + ". Sẵn sàng thả kiện hàng");

        if (demo) {
            // Demo trong nhà: nắp đã mở thật; giả lập drone nhả hàng trong `holdSeconds` giây rồi đóng nắp thật.
            // Đọc lại phiên lúc hẹn giờ chạy (có thể đã đổi).
            String sessionId = session.getSessionId();
            saveLog(UUID.randomUUID().toString(), session.getCorrelationId(), session.getSessionId(),
                    session.getOrderId(), session.getMissionId(), session.getDroneId(), session.getLockerId(),
                    session.getCompartmentId(), session.getTargetMarkerId(), session.getTargetDictionary(),
                    "BACKEND", "LOCKER", "cabinet/" + session.getLockerId() + "/command/open",
                    "DROP_PHASE_STARTED", "INTERNAL", null, "PROCESSED", "CONFIRMED", "SUCCESS", null,
                    "Bắt đầu giả lập drone nhả hàng (" + holdSeconds + " giây) — nắp đã mở thật", 0, null);
            scheduler.schedule(() -> {
                try {
                    sessionRepository.findById(sessionId)
                            .filter(current -> DroneDeliverySession.LID_OPENED.equals(current.getState()))
                            .ifPresent(current -> {
                                saveLog(UUID.randomUUID().toString(), current.getCorrelationId(), current.getSessionId(),
                                        current.getOrderId(), current.getMissionId(), current.getDroneId(), current.getLockerId(),
                                        current.getCompartmentId(), current.getTargetMarkerId(), current.getTargetDictionary(),
                                        "BACKEND", "LOCKER", "cabinet/" + current.getLockerId() + "/command/close",
                                        "DROP_PHASE_COMPLETED", "INTERNAL", null, "PROCESSED", "CONFIRMED", "SUCCESS", null,
                                        "Hoàn tất giả lập drone nhả hàng", 0, null);
                                requestCloseLid(current, "Gửi lệnh yêu cầu đóng nắp trượt ô tủ");
                            });
                } catch (Exception ex) {
                    log.error("[COMM] Error in scheduled close for session {}: {}", sessionId, ex.getMessage(), ex);
                }
            }, holdSeconds, TimeUnit.SECONDS);
        }
    }

    private void handleCloseResult(DroneDeliverySession session, String status, String hwState, String errorCode,
            String errorMessage, String topic, String rawPayload) {
        boolean isSuccess = "SUCCESS".equalsIgnoreCase(status) && "CLOSED".equalsIgnoreCase(hwState);
        saveLog(session.getCloseCommandId(), session.getCorrelationId(), session.getSessionId(), session.getOrderId(),
                session.getMissionId(), session.getDroneId(), session.getLockerId(), session.getCompartmentId(),
                session.getTargetMarkerId(), session.getTargetDictionary(), "LOCKER", "BACKEND", topic,
                isSuccess ? "LID_CLOSED" : "LID_CLOSE_FAILED", "INBOUND", rawPayload, "RECEIVED", "CONFIRMED",
                isSuccess ? "SUCCESS" : "FAILED", errorCode, errorMessage, 0, null);
        if (!isSuccess) {
            session.setState(DroneDeliverySession.LID_CLOSE_FAILED);
            session.setErrorCode(errorCode);
            session.setErrorMessage(errorMessage);
            sessionRepository.save(session);
            publishCommand("LID_CLOSE_FAILED", session.getSessionId(), session.getCorrelationId(), session.getOrderId(),
                    session.getDroneId(), session.getLockerId(), session.getCompartmentId(), errorCode, errorMessage);
            return;
        }

        // Nắp đã đóng đúng lệnh của phiên ⇒ hàng đã vào ô. Chuyển chặng có kiểm: đơn phải còn đang giao, đang ARRIVED.
        String failure;
        try {
            failure = transactions().execute(status2 -> {
                LockerOrder order = orderRepository.findByIdForUpdate(session.getOrderId()).orElse(null);
                DroneMission mission = missionRepository.findByOrderId(session.getOrderId()).orElse(null);
                if (order == null || mission == null || !Objects.equals(mission.getId(), session.getMissionId())) {
                    return "MISSION_NOT_FOUND";
                }
                progressService.advanceToStage(order, mission, "READY_FOR_PICKUP", null,
                        "Nắp ô tủ đã đóng và khoá an toàn — kiện hàng sẵn sàng để lấy");
                return null;
            });
        } catch (BusinessException transition) {
            failure = transition.getCode();
        }
        if (failure != null) {
            session.setState(DroneDeliverySession.ABORTED);
            session.setErrorCode(failure);
            session.setErrorMessage("Nắp đã đóng nhưng đơn không còn ở chặng chờ thả hàng");
            sessionRepository.save(session);
            log.warn("[COMM] Lid closed for session {} but order #{} was not advanced: {}",
                    session.getSessionId(), session.getOrderId(), failure);
            return;
        }
        session.setState(DroneDeliverySession.READY_FOR_PICKUP);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);
        publishCommand("DELIVERY_READY", session.getSessionId(), session.getCorrelationId(), session.getOrderId(),
                session.getDroneId(), session.getLockerId(), session.getCompartmentId(), null,
                "Kiện hàng đã vào ô tủ và đóng nắp thành công");
        recordDroneIotLog(session.getDroneUnitId(), session.getDroneId(), "PI_CONTROLLER", "DISARMED", "SUCCESS",
                session.getOrderId(), "Nắp ô tủ đã đóng và khoá an toàn. Đơn hoàn tất giao drone");
    }

    // =========================================================================
    // 3. LID COMMAND RECOVERY (tủ mất kết nối giữa chừng)
    // =========================================================================

    /// Tủ vừa báo còn sống (Pi gửi heartbeat ngay mỗi lần kết nối lại broker, sau đó mỗi phút): lệnh nắp của
    /// tủ này chưa có kết quả thì gửi lại đúng mã lệnh cũ. Pi dùng clean session nên lệnh gửi lúc tủ mất kết
    /// nối bị mất hẳn. Nắp đã ở đúng vị trí thì Pi vẫn báo SUCCESS, nên gửi lặp không hại.
    void resendPendingLidCommands(Long lockerId, boolean reconnected) {
        if (lockerId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DroneDeliverySession session : sessionRepository.findByLockerIdAndStateIn(lockerId,
                DroneDeliverySession.LID_COMMAND_PENDING_STATES)) {
            retryLidCommand(session, now, true, reconnected);
        }
    }

    /// Quét định kỳ: lệnh nắp chưa được tủ xác nhận (hoặc nắp chạy quá lâu chưa báo xong) thì gửi lại đúng
    /// commandId cũ — Pi tủ nhớ commandId đã chạy nên không chạy motor lần hai. Tủ mất kết nối thì chờ tủ quay lại.
    void retryStalledLidCommands() {
        try {
            LocalDateTime now = LocalDateTime.now();
            sessionRepository.findByStateInOrderByCreatedAtDesc(DroneDeliverySession.LID_COMMAND_PENDING_STATES)
                    .forEach(session -> retryLidCommand(session, now, false, false));
        } catch (Exception e) {
            log.error("[COMM] Error retrying lid commands: {}", e.getMessage(), e);
        }
    }

    private void retryLidCommand(DroneDeliverySession session, LocalDateTime now, boolean lockerJustSeen,
            boolean reconnected) {
        boolean open = !DroneDeliverySession.CLOSE_LID_REQUESTED.equals(session.getState());
        if (DroneDeliverySession.OPEN_LID_REQUESTED.equals(session.getState()) && isOpenRequestExpired(session, now)) {
            expireOpenRequest(session);
            return;
        }
        String commandId = open ? session.getOpenCommandId() : session.getCloseCommandId();
        LidCommandAttempt last = lidCommandAttempts.get(commandId);
        boolean acked = DroneDeliverySession.LID_OPENING.equals(session.getState()) || (last != null && last.acked());
        LocalDateTime lastSentAt = last != null ? last.sentAt() : session.getUpdatedAt();
        // Chưa từng thấy tủ này gửi ACK: có thể là code cũ (không chống chạy trùng) ⇒ đợi đủ thời gian chạy nắp.
        long waitSeconds = acked ? orderRules.lidMotionTimeoutSeconds()
                : reconnected || ackCapableLockers.contains(session.getLockerId()) ? orderRules.lidAckTimeoutSeconds()
                : orderRules.lidMotionTimeoutSeconds();
        if (lastSentAt != null && lastSentAt.plusSeconds(waitSeconds).isAfter(now)) {
            return; // tủ có thể đang nhận / đang chạy nắp
        }
        if (!lockerJustSeen && !isLockerOnline(session.getLockerId(), now)) {
            return; // tủ offline: heartbeat lúc kết nối lại sẽ gửi lại, hạn lệnh mở vẫn được kiểm
        }
        int retry = last != null ? last.retry() : 0;
        if (open && unverifiedOpenCommands.remove(commandId)) {
            failLidCommand(session, true, "LID_UNVERIFIED", "Tủ chỉ báo \"đã mở\" mà không có xác nhận phần cứng "
                    + "(trình giả lập tủ hoặc Pi tủ chưa cập nhật code nắp drone) — không coi là nắp đã mở; cập nhật code "
                    + "iot trên Pi tủ và tắt trình giả lập simulate_demo_cabinet.py rồi thử lại");
            return;
        }
        if (retry >= orderRules.lidMaxRetries()) {
            failLidCommand(session, open, acked ? "LID_MOTION_TIMEOUT" : "LOCKER_NO_ACK",
                    acked ? "Tủ đã nhận lệnh nhưng không báo nắp chạy xong sau " + (retry + 1) + " lần hỏi"
                            : "Tủ không xác nhận nhận lệnh sau " + (retry + 1) + " lần gửi");
            return;
        }
        String expiresAt = open ? openCommandExpiresAt(session) : null;
        if (!sendLockerLidCommand(session.getLockerId(), session.getCompartmentId(), open ? "OPEN" : "CLOSE", commandId,
                expiresAt)) {
            return;
        }
        lidCommandAttempts.put(commandId, new LidCommandAttempt(now, retry + 1, acked));
        saveLog(commandId, session.getCorrelationId(), session.getSessionId(), session.getOrderId(),
                session.getMissionId(), session.getDroneId(), session.getLockerId(), session.getCompartmentId(),
                session.getTargetMarkerId(), session.getTargetDictionary(), "BACKEND", "LOCKER",
                "cabinet/" + session.getLockerId() + "/command/" + (open ? "open" : "close"),
                open ? "OPEN_LID_REQUESTED" : "CLOSE_LID_REQUESTED", "OUTBOUND", null, "SENT", "PENDING", "PENDING",
                null, "Gửi lại lệnh " + (open ? "mở" : "đóng") + " nắp trượt cùng mã lệnh (lần " + (retry + 1) + ", "
                        + (reconnected ? "tủ vừa kết nối lại" : acked ? "nắp chạy quá lâu chưa báo xong"
                        : "tủ chưa xác nhận nhận lệnh") + ")", retry + 1, null);
        log.info("[COMM] Resent {} command {} to locker {} (retry {}, acked={})", open ? "OPEN" : "CLOSE", commandId,
                session.getLockerId(), retry + 1, acked);
    }

    private boolean isLockerOnline(Long lockerId, LocalDateTime now) {
        LocalDateTime seen = lockerId == null ? null : lockerSeenAt.get(lockerId);
        return seen != null && seen.plusSeconds(orderRules.lockerOfflineAfterSeconds()).isAfter(now);
    }

    /// Hết lượt gửi lại khi tủ vẫn online: dừng phiên, báo drone (không thả hàng) và để lại dấu vết cho admin.
    private void failLidCommand(DroneDeliverySession session, boolean open, String errorCode, String errorMessage) {
        String commandId = open ? session.getOpenCommandId() : session.getCloseCommandId();
        lidCommandAttempts.remove(commandId);
        session.setState(open ? DroneDeliverySession.LID_OPEN_FAILED : DroneDeliverySession.LID_CLOSE_FAILED);
        session.setErrorCode(errorCode);
        session.setErrorMessage(errorMessage);
        sessionRepository.save(session);
        saveLog(commandId, session.getCorrelationId(), session.getSessionId(), session.getOrderId(),
                session.getMissionId(), session.getDroneId(), session.getLockerId(), session.getCompartmentId(),
                session.getTargetMarkerId(), session.getTargetDictionary(), "BACKEND", "DRONE",
                "cabinet/" + session.getLockerId() + "/command/" + (open ? "open" : "close"),
                open ? "LID_OPEN_FAILED" : "LID_CLOSE_FAILED", "INTERNAL", null, "PROCESSED", "TIMEOUT", "FAILED",
                errorCode, errorMessage, 0, null);
        publishCommand(open ? "LID_OPEN_FAILED" : "LID_CLOSE_FAILED", session.getSessionId(), session.getCorrelationId(),
                session.getOrderId(), session.getDroneId(), session.getLockerId(), session.getCompartmentId(), errorCode,
                errorMessage);
        log.warn("[COMM] {} command {} of session {} failed: {} {}", open ? "OPEN" : "CLOSE", commandId,
                session.getSessionId(), errorCode, errorMessage);
    }

    /// Lệnh mở nắp chờ quá lâu mà tủ không trả lời: drone hẳn đã rời đi, mở muộn chỉ để ô hở ⇒ kết thúc phiên
    /// và báo drone; drone nhận diện lại mã sẽ mở phiên mới. Lệnh đóng nắp thì không bỏ — hàng đã nằm trong ô.
    void expireStaleOpenRequests() {
        try {
            LocalDateTime now = LocalDateTime.now();
            sessionRepository.findByStateInOrderByCreatedAtDesc(Set.of(DroneDeliverySession.OPEN_LID_REQUESTED)).stream()
                    .filter(session -> isOpenRequestExpired(session, now))
                    .forEach(this::expireOpenRequest);
        } catch (Exception e) {
            log.error("[COMM] Error expiring stale open-lid requests: {}", e.getMessage(), e);
        }
    }

    /// Lệnh mở nắp chờ quá thời gian admin cấu hình (tủ mất kết nối lâu) thì bỏ phiên thay vì mở nắp muộn.
    private boolean isOpenRequestExpired(DroneDeliverySession session, LocalDateTime now) {
        return session.getCreatedAt() != null
                && !session.getCreatedAt().plusSeconds(orderRules.droneOpenLidMaxWaitSeconds()).isAfter(now);
    }

    private void expireOpenRequest(DroneDeliverySession session) {
        long waited = orderRules.droneOpenLidMaxWaitSeconds();
        String errorCode;
        String errorMessage;
        if (unverifiedOpenCommands.remove(session.getOpenCommandId())) {
            // Tủ CÓ trả lời, chỉ là "đã mở" không kèm xác nhận phần cứng — không phải mất kết nối.
            errorCode = "LID_UNVERIFIED";
            errorMessage = "Tủ báo \"đã mở\" nhưng không gửi xác nhận phần cứng (lidConfirmation) — nắp có thể chưa "
                    + "chạy; kiểm tra motor nắp và cờ LID_BYPASS_PHYSICAL_MOTION trên Pi tủ, rồi cho drone quét lại mã";
        } else if (lidCommandAttempts.containsKey(session.getOpenCommandId())
                && lidCommandAttempts.get(session.getOpenCommandId()).acked()) {
            errorCode = "LOCKER_NO_RESULT";
            errorMessage = "Tủ đã nhận lệnh nhưng không báo kết quả mở nắp sau " + waited
                    + " giây — huỷ phiên, drone cần nhận diện lại mã ArUco";
        } else {
            errorCode = "LOCKER_NO_RESPONSE";
            errorMessage = "Tủ không phản hồi lệnh mở nắp sau " + waited + " giây (có thể mất kết nối)"
                    + " — huỷ phiên, drone cần nhận diện lại mã ArUco";
        }
        lidCommandAttempts.remove(session.getOpenCommandId());
        session.setState(DroneDeliverySession.LID_OPEN_FAILED);
        session.setErrorCode(errorCode);
        session.setErrorMessage(errorMessage);
        sessionRepository.save(session);
        saveLog(session.getOpenCommandId(), session.getCorrelationId(), session.getSessionId(), session.getOrderId(),
                session.getMissionId(), session.getDroneId(), session.getLockerId(), session.getCompartmentId(),
                session.getTargetMarkerId(), session.getTargetDictionary(), "BACKEND", "DRONE",
                "cabinet/" + session.getLockerId() + "/command/open", "LID_OPEN_FAILED", "INTERNAL", null,
                "PROCESSED", "TIMEOUT", "FAILED", errorCode, errorMessage, 0, null);
        publishCommand("LID_OPEN_FAILED", session.getSessionId(), session.getCorrelationId(), session.getOrderId(),
                session.getDroneId(), session.getLockerId(), session.getCompartmentId(), errorCode, errorMessage);
        log.warn("[COMM] Open-lid request {} of session {} expired ({}) at locker {}",
                session.getOpenCommandId(), session.getSessionId(), errorCode, session.getLockerId());
    }

    // =========================================================================
    // HELPERS: MQTT PUBLISHING & LOGGING
    // =========================================================================

    private TransactionTemplate transactions() {
        return new TransactionTemplate(transactionManager);
    }

    private BoxCellDto fetchCell(Long boxId) {
        if (boxId == null) {
            return null;
        }
        try {
            var cellResp = lockerDroneClient.getBoxCell(boxId);
            return cellResp == null ? null : cellResp.data();
        } catch (Exception ex) {
            log.error("[COMM] Failed to fetch cell details for box {}: {}", boxId, ex.getMessage());
            return null;
        }
    }

    private void finish(DroneLockerCommLog inLog, String processingStatus, String errorCode, String message) {
        if (inLog == null) {
            return;
        }
        inLog.setProcessingStatus(processingStatus);
        inLog.setErrorCode(errorCode);
        inLog.setErrorMessage(message);
        try {
            commLogRepository.save(inLog);
        } catch (Exception ex) {
            log.error("[COMM] Failed to update comm log: {}", ex.getMessage());
        }
    }

    private static Rejected reject(String code, String message) {
        return new Rejected(code, message);
    }

    /// Từ chối sự kiện (rollback giao dịch đang xử lý).
    private static final class Rejected extends RuntimeException {
        private final String code;

        Rejected(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    /// Hạn của lệnh mở nắp: quá thời điểm này Pi tủ bỏ lệnh (tránh mở muộn do lệnh cũ khi tủ kết nối lại).
    /// Tính từ lúc tạo phiên theo thời gian chờ admin cấu hình — gửi lại vẫn giữ nguyên hạn.
    String openCommandExpiresAt(DroneDeliverySession session) {
        LocalDateTime created = session.getCreatedAt() != null ? session.getCreatedAt() : LocalDateTime.now();
        // Cắt về mili giây: Pi đọc bằng datetime.fromisoformat (Python 3.11+), không cần 9 chữ số lẻ.
        return created.atZone(java.time.ZoneId.systemDefault()).toInstant()
                .plusSeconds(orderRules.droneOpenLidMaxWaitSeconds())
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
    }

    private boolean sendLockerLidCommand(Long lockerId, Long boxId, String action, String commandId, String expiresAt) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            log.error("[COMM] Cannot send {} command: MQTT not connected", action);
            return false;
        }
        try {
            ObjectNode payload = lidCommandPayload(boxId, action, commandId, expiresAt);
            String topic = "cabinet/" + lockerId + "/command/" + action.toLowerCase(Locale.ROOT);
            MqttMessage msg = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            msg.setRetained(false); // lệnh nắp không bao giờ giữ lại trên broker
            mqttClient.publish(topic, msg);
            log.info("[COMM] Published {} to {}: {}", action, topic, payload);
            return true;
        } catch (Exception ex) {
            log.error("[COMM] Failed to publish {} command to locker {}: {}", action, lockerId, ex.getMessage());
            return false;
        }
    }

    /// `lid: true` bảo Pi trượt nắp trên nóc tủ thay vì kích relay cửa trước của ô
    /// (mqtt-contract § 2.1). Lệnh mở ô của khách/KTV/admin không có cờ này.
    ObjectNode lidCommandPayload(Long boxId, String action, String commandId) {
        return lidCommandPayload(boxId, action, commandId, null);
    }

    ObjectNode lidCommandPayload(Long boxId, String action, String commandId, String expiresAt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("commandId", commandId);
        payload.put("boxId", boxId);
        payload.put("box_id", boxId);
        payload.put("action", action.toUpperCase(Locale.ROOT));
        payload.put("lid", true);
        payload.put("timeout", 15);
        payload.put("source", "DRONE_DELIVERY");
        payload.put("issuedAt", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString());
        if (expiresAt != null) {
            payload.put("expiresAt", expiresAt);
        }
        return payload;
    }

    /// Lệnh gửi drone được ký bằng khoá của chính drone đó: Pi chỉ làm theo lệnh có chữ ký đúng.
    void publishCommand(String eventType, String sessionId, String correlationId, Long orderId,
                        String droneId, Long lockerId, Long compartmentId, String errorCode, String errorMessage) {
        publishCommand(eventType, sessionId, correlationId, orderId, droneId, lockerId, compartmentId, errorCode,
                errorMessage, Map.of());
    }

    void publishCommand(String eventType, String sessionId, String correlationId, Long orderId, String droneId,
                        Long lockerId, Long compartmentId, String errorCode, String errorMessage,
                        Map<String, Object> extra) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            return;
        }
        try {
            String body = commandPayload(eventType, sessionId, correlationId, orderId, droneId, lockerId,
                    compartmentId, errorCode, errorMessage, extra);
            MqttMessage msg = new MqttMessage(body.getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            String signature = signCommand(droneId, body);
            if (signature != null) {
                MqttProperties properties = new MqttProperties();
                properties.setUserProperties(List.of(new UserProperty(SIGNATURE_PROPERTY, signature)));
                msg.setProperties(properties);
            }
            mqttClient.publish(DRONE_COMMANDS_TOPIC, msg);
            log.info("[COMM] Published command [{}] to {}: {}", eventType, DRONE_COMMANDS_TOPIC, body);
        } catch (Exception ex) {
            log.error("[COMM] Failed to publish command {}: {}", eventType, ex.getMessage());
        }
    }

    String commandPayload(String eventType, String sessionId, String correlationId, Long orderId, String droneId,
            Long lockerId, Long compartmentId, String errorCode, String errorMessage) {
        return commandPayload(eventType, sessionId, correlationId, orderId, droneId, lockerId, compartmentId, errorCode,
                errorMessage, Map.of());
    }

    String commandPayload(String eventType, String sessionId, String correlationId, Long orderId, String droneId,
            Long lockerId, Long compartmentId, String errorCode, String errorMessage, Map<String, Object> extra) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("messageId", UUID.randomUUID().toString());
        payload.put("correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString());
        payload.put("sessionId", sessionId != null ? sessionId : "");
        payload.put("eventType", eventType);
        payload.put("timestamp", OffsetDateTime.now().toString());
        payload.put("source", "BACKEND");
        if (orderId != null) payload.put("orderId", orderId);
        if (droneId != null) payload.put("droneId", droneId);
        if (lockerId != null) payload.put("lockerId", lockerId);
        if (compartmentId != null) payload.put("compartmentId", compartmentId);
        if (errorCode != null) payload.put("errorCode", errorCode);
        if (errorMessage != null) payload.put("errorMessage", errorMessage);
        extra.forEach((key, value) -> payload.set(key, objectMapper.valueToTree(value)));
        return payload.toString();
    }

    String signCommand(String droneId, String body) {
        if (!StringUtils.hasText(secret) || !StringUtils.hasText(droneId)) {
            return null;
        }
        return DroneTelemetrySigner.sign(DroneTelemetrySigner.deviceKey(secret, droneId), DRONE_COMMANDS_TOPIC, body);
    }

    public DroneLockerCommLog saveLog(
            String messageId, String correlationId, String sessionId, Long orderId, Long journeyId,
            String droneId, Long lockerId, Long compartmentId, Integer markerId, String markerDictionary,
            String sourceDevice, String destinationDevice, String mqttTopic, String eventType,
            String direction, String payload, String publishStatus, String ackStatus, String processingStatus,
            String errorCode, String errorMessage, int retryCount, Long durationMs) {
        try {
            DroneLockerCommLog logEntity = new DroneLockerCommLog();
            logEntity.setTimestamp(LocalDateTime.now());
            logEntity.setMessageId(messageId);
            logEntity.setCorrelationId(correlationId);
            logEntity.setSessionId(sessionId);
            logEntity.setOrderId(orderId);
            logEntity.setJourneyId(journeyId);
            logEntity.setDroneId(droneId);
            logEntity.setLockerId(lockerId);
            logEntity.setCompartmentId(compartmentId);
            logEntity.setMarkerId(markerId);
            logEntity.setMarkerDictionary(markerDictionary);
            logEntity.setSourceDevice(sourceDevice);
            logEntity.setDestinationDevice(destinationDevice);
            logEntity.setMqttTopic(mqttTopic);
            logEntity.setEventType(eventType == null ? "UNKNOWN" : eventType);
            logEntity.setDirection(direction);
            logEntity.setPayload(payload);
            logEntity.setPublishStatus(publishStatus);
            logEntity.setAckStatus(ackStatus);
            logEntity.setProcessingStatus(processingStatus);
            logEntity.setErrorCode(errorCode);
            logEntity.setErrorMessage(errorMessage);
            logEntity.setRetryCount(retryCount);
            logEntity.setProcessingDurationMs(durationMs);
            return commLogRepository.save(logEntity);
        } catch (Exception ex) {
            log.error("[COMM] Failed to persist comm log: {}", ex.getMessage());
            return null;
        }
    }

    private void recordDroneIotLog(Long droneUnitId, String droneCode, String component, String eventType,
                                   String result, Long orderId, String message) {
        if (droneUnitId == null) {
            return;
        }
        try {
            com.huynqb.laundrylocker.order.dto.CreateDroneIotLogDto req =
                    new com.huynqb.laundrylocker.order.dto.CreateDroneIotLogDto(
                            droneUnitId, droneCode, component, eventType, result, "SYSTEM", "DroneCommService", orderId, message);
            lockerDroneClient.recordDroneIotLog(droneUnitId, req);
        } catch (Exception ex) {
            log.warn("[COMM] Failed to record DroneIotLog for drone #{}: {}", droneUnitId, ex.getMessage());
        }
    }

    /// Phiên giao đang chạy (đọc từ database — còn nguyên sau khi khởi động lại).
    public List<DeliverySessionState> getActiveSessions() {
        return sessionRepository.findByStateInOrderByCreatedAtDesc(DroneDeliverySession.ACTIVE_STATES).stream()
                .map(DeliverySessionState::of)
                .toList();
    }

    private static Instant instantOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
            try {
                return Instant.parse(value);
            } catch (Exception alsoIgnored) {
                return null;
            }
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Long longOrNull(JsonNode node, String field) {
        if (!node.hasNonNull(field)) return null;
        try {
            return node.get(field).asLong();
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (!node.hasNonNull(field)) return null;
        try {
            return node.get(field).asInt();
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseLongOrNull(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }
}
