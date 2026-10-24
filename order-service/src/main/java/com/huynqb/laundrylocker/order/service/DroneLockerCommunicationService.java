package com.huynqb.laundrylocker.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.dto.BoxCellDto;
import com.huynqb.laundrylocker.order.model.DroneLockerCommLog;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DroneLockerCommunicationService {

    public static final String DRONE_EVENTS_TOPIC = "lockr/drone/delivery/events";
    public static final String DRONE_COMMANDS_TOPIC = "lockr/drone/delivery/commands";
    public static final String LOCKER_RESULT_SUBSCRIPTION = "cabinet/+/command/+/result";

    private final DroneLockerCommLogRepository commLogRepository;
    private final LockerOrderRepository orderRepository;
    private final DroneMissionRepository missionRepository;
    private final DroneMissionProgressService progressService;
    private final LockerDroneClient lockerDroneClient;
    private final OrderRules orderRules;
    private final ObjectMapper objectMapper;

    @Value("${app.drone.telemetry.broker-url:tcp://broker.hivemq.com:1883}")
    private String brokerUrl;

    @Value("${app.drone.telemetry.username:}")
    private String username;

    @Value("${app.drone.telemetry.password:}")
    private String password;

    private MqttClient mqttClient;

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

    // Active session cache
    private final ConcurrentHashMap<String, DeliverySessionState> activeSessions = new ConcurrentHashMap<>();
    // Lookup by lockerId
    private final ConcurrentHashMap<Long, String> sessionByLocker = new ConcurrentHashMap<>();
    // Lookup by openCommandId or closeCommandId
    private final ConcurrentHashMap<String, String> sessionByCommand = new ConcurrentHashMap<>();

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
        private String state; // State machine state
        private String openCommandId;
        private String closeCommandId;
        private LocalDateTime lidOpenedAt;
        private LocalDateTime createdAt = LocalDateTime.now();
    }

    @PostConstruct
    public void init() {
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
                    eventExecutor.execute(() -> handleIncomingMessage(topic, payload));
                }
            });

            connectOrRetry(options);
        } catch (MqttException e) {
            log.error("[COMM] Failed to create MQTT client for {}", brokerUrl, e);
        }
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
            String[] topics = new String[]{DRONE_EVENTS_TOPIC, LOCKER_RESULT_SUBSCRIPTION};
            int[] qos = new int[]{1, 1};
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

    // =========================================================================
    // MQTT INCOMING MESSAGE ROUTING
    // =========================================================================

    public void handleIncomingMessage(String topic, String payload) {
        log.info("[COMM] Incoming MQTT [{}] -> {}", topic, payload);
        try {
            JsonNode data = objectMapper.readTree(payload);

            if (DRONE_EVENTS_TOPIC.equals(topic)) {
                handleDroneEvent(data, payload);
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

    private void handleDroneEvent(JsonNode data, String rawPayload) {
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

        // Log inbound event
        DroneLockerCommLog inLog = saveLog(
                messageId, correlationId, sessionId, orderId, null, droneId, lockerId, compartmentId,
                markerId, dictionary, "DRONE", "BACKEND", DRONE_EVENTS_TOPIC, eventType,
                "INBOUND", rawPayload, "DELIVERED", "RECEIVED", "PROCESSING", null, null, 0, null);

        if ("MARKER_DETECTED".equalsIgnoreCase(eventType)) {
            processMarkerDetected(data, inLog, mode);
        } else if ("DROP_PHASE_COMPLETED".equalsIgnoreCase(eventType)) {
            processDropPhaseCompleted(data, inLog);
        } else if ("SAFETY_ABORT".equalsIgnoreCase(eventType)) {
            processSafetyAbort(data, inLog);
        }
    }

    @Transactional
    public void processMarkerDetected(JsonNode data, DroneLockerCommLog inLog, String mode) {
        Long orderId = inLog.getOrderId();
        String droneId = inLog.getDroneId();
        Long lockerId = inLog.getLockerId();
        Long compartmentId = inLog.getCompartmentId();
        String detectedDict = inLog.getMarkerDictionary() != null ? inLog.getMarkerDictionary() : "DICT_4X4_50";
        Integer detectedMarkerId = inLog.getMarkerId();

        // 1. Resolve order (use findById instead of findByIdForUpdate to avoid TransactionRequiredException on self-invocation)
        LockerOrder order = null;
        if (orderId != null) {
            order = orderRepository.findById(orderId).orElse(null);
        }
        if (order == null) {
            // Find active drone order in flight (prefer newest & matching marker or droneCode)
            List<DroneMission> inFlights = new ArrayList<>(
                    missionRepository.findByStatusIn(DroneMissionProgressService.IN_FLIGHT_STAGES));
            inFlights.sort(Comparator.comparing(DroneMission::getId, Comparator.nullsLast(Comparator.reverseOrder())));

            LockerOrder fallbackCandidate = null;
            for (DroneMission m : inFlights) {
                LockerOrder candidate = orderRepository.findById(m.getOrderId()).orElse(null);
                if (candidate == null || !"DRONE_DELIVERY".equalsIgnoreCase(candidate.getType())) {
                    continue;
                }
                if (lockerId != null && !lockerId.equals(candidate.getLockerId())) {
                    continue;
                }
                if (fallbackCandidate == null) {
                    fallbackCandidate = candidate;
                }
                Long candBoxId = candidate.getReservedBoxId() != null ? candidate.getReservedBoxId() : candidate.getSendBoxId();
                if (candBoxId != null && detectedMarkerId != null) {
                    try {
                        var resp = lockerDroneClient.getBoxCell(candBoxId);
                        if (resp != null && resp.data() != null && detectedMarkerId.equals(resp.data().arucoMarkerId())) {
                            order = candidate;
                            orderId = order.getId();
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            if (order == null && fallbackCandidate != null) {
                order = fallbackCandidate;
                orderId = order.getId();
            }
        }

        // In INDOOR_ARUCO_DEMO mode, also allow matching newly created DRONE_DELIVERY orders
        // that have not yet been manually launched by a technician.
        if (order == null && "INDOOR_ARUCO_DEMO".equalsIgnoreCase(mode)) {
            List<LockerOrder> openOrders = orderRepository.findByTypeAndStatusNotInOrderByUpdatedAtDesc(
                    "DRONE_DELIVERY",
                    List.of("COMPLETED", "CANCELED", "EXPIRED", "STORING", "DELIVERY_INCOMPLETE"));
            LockerOrder fallbackOpen = null;
            for (LockerOrder candidate : openOrders) {
                if (lockerId != null && !lockerId.equals(candidate.getLockerId())) {
                    continue;
                }
                if (fallbackOpen == null) {
                    fallbackOpen = candidate;
                }
                Long candBoxId = candidate.getReservedBoxId() != null ? candidate.getReservedBoxId() : candidate.getSendBoxId();
                if (candBoxId != null && detectedMarkerId != null) {
                    try {
                        var resp = lockerDroneClient.getBoxCell(candBoxId);
                        if (resp != null && resp.data() != null && detectedMarkerId.equals(resp.data().arucoMarkerId())) {
                            order = candidate;
                            orderId = order.getId();
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            if (order == null && fallbackOpen != null) {
                order = fallbackOpen;
                orderId = order.getId();
            }
        }

        if (order == null) {
            inLog.setProcessingStatus("REJECTED");
            inLog.setErrorCode("ORDER_NOT_FOUND");
            inLog.setErrorMessage("Không tìm thấy đơn hàng giao drone đang bay phù hợp");
            commLogRepository.save(inLog);
            publishCommand("SAFETY_ABORT", inLog.getSessionId(), inLog.getCorrelationId(), orderId, droneId,
                    lockerId, compartmentId, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng hợp lệ");
            return;
        }

        DroneMission mission = missionRepository.findByOrderId(order.getId()).orElse(null);
        if (mission == null && "INDOOR_ARUCO_DEMO".equalsIgnoreCase(mode)) {
            mission = new DroneMission();
            mission.setOrderId(order.getId());
            mission.setDroneCode(droneId != null ? droneId : "DRONE-DEMO");
            mission.setSourceLockerId(order.getSourceLockerId() != null ? order.getSourceLockerId() : order.getLockerId());
            mission.setDestinationLockerId(order.getDestinationLockerId() != null ? order.getDestinationLockerId() : order.getLockerId());
            mission.setStatus("APPROACHING");
            mission.setLaunchingAt(LocalDateTime.now());
            mission = missionRepository.save(mission);
        }
        if (mission == null) {
            inLog.setProcessingStatus("REJECTED");
            inLog.setErrorCode("MISSION_NOT_FOUND");
            inLog.setErrorMessage("Không tìm thấy DroneMission cho đơn #" + orderId);
            commLogRepository.save(inLog);
            return;
        }

        Long targetBoxId = order.getReservedBoxId() != null ? order.getReservedBoxId() : order.getSendBoxId();
        Long destLockerId = order.getLockerId();
        if (compartmentId == null) {
            compartmentId = targetBoxId;
        }
        if (lockerId == null) {
            lockerId = destLockerId;
        }

        // 2. Fetch target box ArUco configuration from locker-service
        BoxCellDto targetCell = null;
        try {
            var cellResp = lockerDroneClient.getBoxCell(targetBoxId);
            if (cellResp != null && cellResp.data() != null) {
                targetCell = cellResp.data();
            }
        } catch (Exception ex) {
            log.error("[COMM] Failed to fetch cell details for box {}: {}", targetBoxId, ex.getMessage());
        }

        Integer expectedMarkerId = targetCell != null ? targetCell.arucoMarkerId() : null;
        String expectedDict = targetCell != null && targetCell.arucoDictionary() != null
                ? targetCell.arucoDictionary() : "DICT_4X4_50";

        // 3. VALIDATE ARUCO MARKER
        boolean markerValid = expectedMarkerId != null
                && expectedMarkerId.equals(detectedMarkerId)
                && expectedDict.equalsIgnoreCase(detectedDict);

        if (!markerValid) {
            String errMsg = String.format("Mã ArUco phát hiện (#%s, %s) không khớp với ô đích #%s (cần mã #%s, %s)",
                    detectedMarkerId, detectedDict, targetBoxId, expectedMarkerId, expectedDict);
            inLog.setProcessingStatus("REJECTED");
            inLog.setErrorCode("WRONG_MARKER");
            inLog.setErrorMessage(errMsg);
            commLogRepository.save(inLog);

            saveLog(UUID.randomUUID().toString(), inLog.getCorrelationId(), inLog.getSessionId(),
                    orderId, mission.getId(), droneId, lockerId, compartmentId,
                    detectedMarkerId, detectedDict, "BACKEND", "DRONE", DRONE_COMMANDS_TOPIC,
                    "MARKER_REJECTED", "OUTBOUND", null, "SENT", "N/A", "REJECTED",
                    "WRONG_MARKER", errMsg, 0, null);

            publishCommand("MARKER_REJECTED", inLog.getSessionId(), inLog.getCorrelationId(), orderId, droneId,
                    lockerId, compartmentId, "WRONG_MARKER", errMsg);

            log.warn("[COMM] WRONG_MARKER rejected: {}", errMsg);
            recordDroneIotLog(mission.getDroneUnitId(), droneId, "VISION_CAMERA", "MARKER_MISMATCH", "FAILED", orderId, errMsg);
            return;
        }

        // 4. Marker is VALID! Update session state
        String sessionId = inLog.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "SESSION-" + orderId + "-" + System.currentTimeMillis();
        }
        String correlationId = inLog.getCorrelationId() != null ? inLog.getCorrelationId() : UUID.randomUUID().toString();

        DeliverySessionState session = new DeliverySessionState();
        session.setSessionId(sessionId);
        session.setCorrelationId(correlationId);
        session.setOrderId(orderId);
        session.setJourneyId(mission.getId());
        session.setDroneId(droneId);
        session.setLockerId(destLockerId);
        session.setCompartmentId(targetBoxId);
        session.setTargetDictionary(expectedDict);
        session.setTargetMarkerId(expectedMarkerId);
        session.setDroneUnitId(mission.getDroneUnitId());
        session.setMode(mode);
        session.setState("MARKER_VALIDATED");

        activeSessions.put(sessionId, session);
        sessionByLocker.put(destLockerId, sessionId);

        inLog.setProcessingStatus("SUCCESS");
        inLog.setOrderId(orderId);
        inLog.setJourneyId(mission.getId());
        inLog.setLockerId(destLockerId);
        inLog.setCompartmentId(targetBoxId);
        commLogRepository.save(inLog);

        // 5. Log & publish MARKER_VALIDATED to Drone
        saveLog(UUID.randomUUID().toString(), correlationId, sessionId,
                orderId, mission.getId(), droneId, destLockerId, targetBoxId,
                expectedMarkerId, expectedDict, "BACKEND", "DRONE", DRONE_COMMANDS_TOPIC,
                "MARKER_VALIDATED", "OUTBOUND", null, "SENT", "N/A", "SUCCESS",
                null, "Xác thực mã ArUco hợp lệ", 0, null);

        publishCommand("MARKER_VALIDATED", sessionId, correlationId, orderId, droneId,
                destLockerId, targetBoxId, null, null);

        recordDroneIotLog(mission.getDroneUnitId(), droneId, "VISION_CAMERA", "DETECT_TARGET", "SUCCESS", orderId,
                "Camera AI thi giac nhan dien ma ArUco #" + detectedMarkerId + " (" + detectedDict + ")");
        recordDroneIotLog(mission.getDroneUnitId(), droneId, "PI_CONTROLLER", "ARUCO_DETECTED", "SUCCESS", orderId,
                "Xac thuc ma ArUco #" + expectedMarkerId + " hop le cho o #" + targetBoxId + " (Tu #" + destLockerId + ")");
        recordDroneIotLog(mission.getDroneUnitId(), droneId, "TELEMETRY", "TELEMETRY_ONLINE", "SUCCESS", orderId,
                "Gui lenh yeu cau mo nap truot toi tu #" + destLockerId + " (o #" + targetBoxId + ")");

        // 6. IMMEDIATELY ADVANCE JOURNEY TO "ARRIVED" (Đã tới nơi)
        // Admin & Customer progress bar instantly ticks green up to ARRIVED!
        log.info("[COMM] ArUco validated for order #{}. Advancing mission to ARRIVED...", orderId);
        try {
            progressService.advanceToStage(order, mission, "ARRIVED", null,
                    "Camera drone nhận diện đúng mã ArUco #" + expectedMarkerId + " của ô tủ");
        } catch (Exception ex) {
            log.error("[COMM] Error advancing order to ARRIVED: {}", ex.getMessage());
        }

        // 7. PUBLISH OPEN LID COMMAND TO LOCKER
        String openCmdId = UUID.randomUUID().toString();
        session.setOpenCommandId(openCmdId);
        session.setState("OPEN_LID_REQUESTED");
        sessionByCommand.put(openCmdId, sessionId);

        sendLockerLidCommand(destLockerId, targetBoxId, "OPEN", openCmdId);

        saveLog(openCmdId, correlationId, sessionId,
                orderId, mission.getId(), droneId, destLockerId, targetBoxId,
                expectedMarkerId, expectedDict, "BACKEND", "LOCKER", "cabinet/" + destLockerId + "/command/open",
                "OPEN_LID_REQUESTED", "OUTBOUND", null, "SENT", "PENDING", "PENDING",
                null, "Yêu cầu mở nắp trượt ô tủ", 0, null);
    }

    private void processDropPhaseCompleted(JsonNode data, DroneLockerCommLog inLog) {
        String sessionId = inLog.getSessionId();
        DeliverySessionState session = sessionId != null ? activeSessions.get(sessionId) : null;
        if (session == null && inLog.getLockerId() != null) {
            String sid = sessionByLocker.get(inLog.getLockerId());
            if (sid != null) session = activeSessions.get(sid);
        }

        inLog.setProcessingStatus("SUCCESS");
        commLogRepository.save(inLog);

        if (session != null) {
            recordDroneIotLog(session.getDroneUnitId(), session.getDroneId(), "CARGO_HOOK", "HOOK_RELEASE", "RELEASED", session.getOrderId(),
                    "Moc treo nhan tin hieu va nha kien hang an toan vao o tu");
            requestCloseLid(session, "Drone gửi xác nhận hoàn tất giai đoạn nhả hàng");
        }
    }

    private void processSafetyAbort(JsonNode data, DroneLockerCommLog inLog) {
        inLog.setProcessingStatus("ABORTED");
        commLogRepository.save(inLog);
        log.warn("[COMM] Safety abort received: {}", inLog.getPayload());
    }

    // =========================================================================
    // 2. LOCKER RESULTS (Locker -> Backend)
    // =========================================================================

    private void handleLockerResult(String topic, JsonNode data, String rawPayload) {
        String commandId = text(data, "commandId");
        Long boxId = longOrNull(data, "boxId");
        String status = text(data, "status");
        String hwState = text(data, "hwState");
        String errorCode = text(data, "errorCode");
        String errorMessage = text(data, "errorMessage");

        String[] parts = topic.split("/");
        Long lockerId = (parts.length >= 2) ? parseLongOrNull(parts[1]) : null;
        String action = (parts.length >= 4) ? parts[3] : "unknown";

        String sessionId = commandId != null ? sessionByCommand.get(commandId) : null;
        if (sessionId == null && lockerId != null) {
            sessionId = sessionByLocker.get(lockerId);
        }
        DeliverySessionState session = sessionId != null ? activeSessions.get(sessionId) : null;

        Long orderId = session != null ? session.getOrderId() : null;
        String correlationId = session != null ? session.getCorrelationId() : null;
        String droneId = session != null ? session.getDroneId() : null;

        if ("open".equalsIgnoreCase(action)) {
            handleOpenResult(commandId, lockerId, boxId, status, hwState, errorCode, errorMessage,
                    session, correlationId, orderId, droneId, rawPayload);
        } else if ("close".equalsIgnoreCase(action)) {
            handleCloseResult(commandId, lockerId, boxId, status, hwState, errorCode, errorMessage,
                    session, correlationId, orderId, droneId, rawPayload);
        }
    }

    @Transactional
    public void handleOpenResult(String commandId, Long lockerId, Long boxId, String status, String hwState,
                                 String errorCode, String errorMessage, DeliverySessionState session,
                                 String correlationId, Long orderId, String droneId, String rawPayload) {
        boolean isSuccess = "SUCCESS".equalsIgnoreCase(status) && "OPEN".equalsIgnoreCase(hwState);
        String eventType = isSuccess ? "LID_OPENED" : "LID_OPEN_FAILED";

        saveLog(commandId, correlationId, session != null ? session.getSessionId() : null,
                orderId, session != null ? session.getJourneyId() : null, droneId, lockerId, boxId,
                session != null ? session.getTargetMarkerId() : null,
                session != null ? session.getTargetDictionary() : null,
                "LOCKER", "BACKEND", "cabinet/" + lockerId + "/command/open/result", eventType,
                "INBOUND", rawPayload, "RECEIVED", "CONFIRMED", isSuccess ? "SUCCESS" : "FAILED",
                errorCode, errorMessage, 0, null);

        if (!isSuccess) {
            log.error("[COMM] Locker {} open failed: {} - {}", lockerId, errorCode, errorMessage);
            publishCommand("LID_OPEN_FAILED", session != null ? session.getSessionId() : null,
                    correlationId, orderId, droneId, lockerId, boxId, errorCode, errorMessage);
            return;
        }

        log.info("[COMM] Locker {} lid confirmed OPEN for session {}", lockerId, session != null ? session.getSessionId() : null);
        if (session != null) {
            session.setState("LID_OPENED");
            session.setLidOpenedAt(LocalDateTime.now());

            // Notify drone that lid is fully open
            publishCommand("LID_OPENED", session.getSessionId(), correlationId, orderId, droneId,
                    lockerId, boxId, null, "Nắp ô tủ đã mở hoàn toàn");
            recordDroneIotLog(session.getDroneUnitId(), droneId, "CARGO_HOOK", "HOOK_ATTACH", "SUCCESS", orderId,
                    "Nap o tu da mo hoan toan. San sang tiep can tha kien hang");

            // Check mode
            if ("INDOOR_ARUCO_DEMO".equalsIgnoreCase(session.getMode())) {
                // In Indoor Demo mode: start dynamic hold-open period automatically
                int holdSeconds = orderRules.droneDemoHoldOpenSeconds();
                if (holdSeconds <= 0) {
                    holdSeconds = 7;
                }
                log.info("[COMM] Starting {}-second hold open period for session {}...", holdSeconds, session.getSessionId());

                saveLog(UUID.randomUUID().toString(), correlationId, session.getSessionId(),
                        orderId, session.getJourneyId(), droneId, lockerId, boxId,
                        session.getTargetMarkerId(), session.getTargetDictionary(),
                        "BACKEND", "SYSTEM", "internal/timer", "DROP_PHASE_STARTED",
                        "INTERNAL", null, "COMPLETED", "CONFIRMED", "SUCCESS",
                        null, "Bắt đầu giữ mở nắp trượt trong " + holdSeconds + " giây", 0, null);

                final int scheduledHoldSeconds = holdSeconds;
                scheduler.schedule(() -> {
                    try {
                        log.info("[COMM] {}-second hold complete. Requesting close lid for session {}...", scheduledHoldSeconds, session.getSessionId());

                        saveLog(UUID.randomUUID().toString(), correlationId, session.getSessionId(),
                                orderId, session.getJourneyId(), droneId, lockerId, boxId,
                                session.getTargetMarkerId(), session.getTargetDictionary(),
                                "BACKEND", "SYSTEM", "internal/timer", "DROP_PHASE_COMPLETED",
                                "INTERNAL", null, "COMPLETED", "CONFIRMED", "SUCCESS",
                                null, "Hoàn tất " + scheduledHoldSeconds + " giây giữ mở nắp", 0, null);

                        requestCloseLid(session, "Hết " + scheduledHoldSeconds + " giây giữ mở nắp trượt");
                    } catch (Exception ex) {
                        log.error("[COMM] Error in scheduled close: {}", ex.getMessage(), ex);
                    }
                }, scheduledHoldSeconds, TimeUnit.SECONDS);

            } else {
                // Real outdoor: wait for drone to send DROP_PHASE_COMPLETED
                log.info("[COMM] Outdoor mode: Waiting for Drone to complete drop phase and send DROP_PHASE_COMPLETED...");
            }
        }
    }

    public void requestCloseLid(DeliverySessionState session, String reason) {
        Long lockerId = session.getLockerId();
        Long boxId = session.getCompartmentId();
        String closeCmdId = UUID.randomUUID().toString();

        session.setCloseCommandId(closeCmdId);
        session.setState("CLOSE_LID_REQUESTED");
        sessionByCommand.put(closeCmdId, session.getSessionId());

        sendLockerLidCommand(lockerId, boxId, "CLOSE", closeCmdId);

        saveLog(closeCmdId, session.getCorrelationId(), session.getSessionId(),
                session.getOrderId(), session.getJourneyId(), session.getDroneId(), lockerId, boxId,
                session.getTargetMarkerId(), session.getTargetDictionary(),
                "BACKEND", "LOCKER", "cabinet/" + lockerId + "/command/close",
                "CLOSE_LID_REQUESTED", "OUTBOUND", null, "SENT", "PENDING", "PENDING",
                null, reason, 0, null);
    }

    @Transactional
    public void handleCloseResult(String commandId, Long lockerId, Long boxId, String status, String hwState,
                                  String errorCode, String errorMessage, DeliverySessionState session,
                                  String correlationId, Long orderId, String droneId, String rawPayload) {
        boolean isSuccess = "SUCCESS".equalsIgnoreCase(status) && "CLOSED".equalsIgnoreCase(hwState);
        String eventType = isSuccess ? "LID_CLOSED" : "LID_CLOSE_FAILED";

        saveLog(commandId, correlationId, session != null ? session.getSessionId() : null,
                orderId, session != null ? session.getJourneyId() : null, droneId, lockerId, boxId,
                session != null ? session.getTargetMarkerId() : null,
                session != null ? session.getTargetDictionary() : null,
                "LOCKER", "BACKEND", "cabinet/" + lockerId + "/command/close/result", eventType,
                "INBOUND", rawPayload, "RECEIVED", "CONFIRMED", isSuccess ? "SUCCESS" : "FAILED",
                errorCode, errorMessage, 0, null);

        if (!isSuccess) {
            log.error("[COMM] Locker {} close failed: {} - {}", lockerId, errorCode, errorMessage);
            publishCommand("LID_CLOSE_FAILED", session != null ? session.getSessionId() : null,
                    correlationId, orderId, droneId, lockerId, boxId, errorCode, errorMessage);
            return;
        }

        log.info("[COMM] Locker {} lid confirmed CLOSED for session {}. Marking READY_FOR_PICKUP!",
                lockerId, session != null ? session.getSessionId() : null);

        if (session != null) {
            session.setState("LID_CLOSED");
        }

        // ONLY NOW THAT LID IS CONFIRMED CLOSED: ADVANCE ORDER TO READY_FOR_PICKUP!
        if (orderId != null) {
            LockerOrder order = orderRepository.findById(orderId).orElse(null);
            DroneMission mission = missionRepository.findByOrderId(orderId).orElse(null);
            if (order != null && mission != null) {
                try {
                    progressService.advanceToStage(order, mission, "READY_FOR_PICKUP", null,
                            "Nắp ô tủ đã đóng hoàn toàn và khoá an toàn — Kiện hàng sẵn sàng để lấy");
                    log.info("[COMM] Order #{} successfully moved to READY_FOR_PICKUP (STORING)", orderId);
                } catch (Exception ex) {
                    log.error("[COMM] Error advancing order to READY_FOR_PICKUP: {}", ex.getMessage(), ex);
                }
            }
        }

        // Log DELIVERY_READY
        saveLog(UUID.randomUUID().toString(), correlationId, session != null ? session.getSessionId() : null,
                orderId, session != null ? session.getJourneyId() : null, droneId, lockerId, boxId,
                session != null ? session.getTargetMarkerId() : null,
                session != null ? session.getTargetDictionary() : null,
                "BACKEND", "DRONE", DRONE_COMMANDS_TOPIC, "DELIVERY_READY",
                "OUTBOUND", null, "SENT", "CONFIRMED", "SUCCESS",
                null, "Giao hàng hoàn tất an toàn - Kiện hàng sẵn sàng để khách nhận", 0, null);

        publishCommand("DELIVERY_READY", session != null ? session.getSessionId() : null,
                correlationId, orderId, droneId, lockerId, boxId, null,
                "Kiện hàng đã vào ô tủ và đóng nắp thành công");
        recordDroneIotLog(session != null ? session.getDroneUnitId() : null, droneId, "PI_CONTROLLER", "DISARMED", "SUCCESS", orderId,
                "Nap o tu da dong va khoa an toan. Don hang hoan tat giao drone");

        if (session != null) {
            session.setState("READY_FOR_PICKUP");
            // Session completed, cleanup from locker mapping
            sessionByLocker.remove(lockerId);
        }
    }

    // =========================================================================
    // HELPERS: MQTT PUBLISHING & LOGGING
    // =========================================================================

    private void sendLockerLidCommand(Long lockerId, Long boxId, String action, String commandId) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            log.error("[COMM] Cannot send {} command: MQTT not connected", action);
            return;
        }
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("commandId", commandId);
            payload.put("boxId", boxId);
            payload.put("box_id", boxId);
            payload.put("action", action.toUpperCase(Locale.ROOT));
            payload.put("timeout", 15);

            String topic = "cabinet/" + lockerId + "/command/" + action.toLowerCase(Locale.ROOT);
            MqttMessage msg = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            mqttClient.publish(topic, msg);
            log.info("[COMM] Published {} to {}: {}", action, topic, payload);
        } catch (Exception ex) {
            log.error("[COMM] Failed to publish {} command to locker {}: {}", action, lockerId, ex.getMessage());
        }
    }

    private void publishCommand(String eventType, String sessionId, String correlationId, Long orderId,
                                String droneId, Long lockerId, Long compartmentId, String errorCode, String errorMessage) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            return;
        }
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("messageId", UUID.randomUUID().toString());
            payload.put("correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString());
            payload.put("sessionId", sessionId != null ? sessionId : "");
            payload.put("eventType", eventType);
            payload.put("timestamp", LocalDateTime.now().toString());
            payload.put("source", "BACKEND");
            if (orderId != null) payload.put("orderId", orderId);
            if (droneId != null) payload.put("droneId", droneId);
            if (lockerId != null) payload.put("lockerId", lockerId);
            if (compartmentId != null) payload.put("compartmentId", compartmentId);
            if (errorCode != null) payload.put("errorCode", errorCode);
            if (errorMessage != null) payload.put("errorMessage", errorMessage);

            MqttMessage msg = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            mqttClient.publish(DRONE_COMMANDS_TOPIC, msg);
            log.info("[COMM] Published command [{}] to {}: {}", eventType, DRONE_COMMANDS_TOPIC, payload);
        } catch (Exception ex) {
            log.error("[COMM] Failed to publish command {}: {}", eventType, ex.getMessage());
        }
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
            logEntity.setEventType(eventType);
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
            log.info("[COMM] Recorded DroneIotLog: {}/{} for drone #{}", component, eventType, droneUnitId);
        } catch (Exception ex) {
            log.warn("[COMM] Failed to record DroneIotLog for drone #{}: {}", droneUnitId, ex.getMessage());
        }
    }

    public List<DeliverySessionState> getActiveSessions() {
        return new ArrayList<>(activeSessions.values());
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
