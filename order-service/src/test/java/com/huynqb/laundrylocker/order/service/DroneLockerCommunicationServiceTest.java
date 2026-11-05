package com.huynqb.laundrylocker.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.BoxCellDto;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.model.DroneDeliverySession;
import com.huynqb.laundrylocker.order.model.DroneLockerCommLog;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneDeliverySessionRepository;
import com.huynqb.laundrylocker.order.repository.DroneLockerCommLogRepository;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/// Kênh MQTT drone ↔ tủ: chữ ký, độ mới, chống phát lại, định danh theo drone, kết quả nắp khớp mã lệnh,
/// phiên lưu database, và không bao giờ mở lại đơn đã kết thúc.
class DroneLockerCommunicationServiceTest {

    private static final String SECRET = "test-master-secret";
    private static final String DRONE = "DRONE-A1";
    private static final long LOCKER = 5L;
    private static final long BOX = 9001L;

    private final DroneLockerCommLogRepository commLogRepository = mock(DroneLockerCommLogRepository.class);
    private final LockerOrderRepository orderRepository = mock(LockerOrderRepository.class);
    private final DroneMissionRepository missionRepository = mock(DroneMissionRepository.class);
    private final OrderStatusHistoryRepository historyRepository = mock(OrderStatusHistoryRepository.class);
    private final NotificationClient notificationClient = mock(NotificationClient.class);
    private final LockerDroneClient lockerDroneClient = mock(LockerDroneClient.class);
    private final LockerClient lockerClient = mock(LockerClient.class);
    private final UserClient userClient = mock(UserClient.class);
    private final DroneDeliveryMailer mailer = mock(DroneDeliveryMailer.class);
    private final DroneDeliverySessionRepository sessionRepository = mock(DroneDeliverySessionRepository.class);
    /// Quy tắc trong bộ nhớ: test đổi thời gian chờ / số lần gửi lại như admin sửa trên web.
    private final com.huynqb.laundrylocker.common.settings.BusinessSettings settings = TestOrderRules.settings(Map.of());
    private final OrderRules rules = new OrderRules(settings);

    private final Map<String, DroneDeliverySession> sessions = new LinkedHashMap<>();
    private final List<String> seenMessageIds = new ArrayList<>();
    private LockerOrder order;
    private DroneMission mission;
    private DroneLockerCommunicationService service;

    /// Giao dịch giả: chạy thẳng, lỗi thì để ngoại lệ đi ra như TransactionTemplate thật.
    private static final PlatformTransactionManager DIRECT = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    };

    @BeforeEach
    void setUp() {
        DroneMissionProgressService progress = new DroneMissionProgressService(missionRepository, orderRepository,
                historyRepository, notificationClient, lockerDroneClient, lockerClient, userClient, rules, mailer);
        service = new DroneLockerCommunicationService(commLogRepository, orderRepository, missionRepository, progress,
                lockerDroneClient, rules, new ObjectMapper(), sessionRepository, DIRECT);
        ReflectionTestUtils.setField(service, "secret", SECRET);

        order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(44L);
        order.setReceiverEmail("nhan@gmail.com");
        order.setType("DRONE_DELIVERY");
        order.setFulfillmentMode("STANDARD");
        order.setStatus("AWAITING_DISPATCH");
        order.setDeliveryStage("APPROACHING");
        order.setLockerId(LOCKER);
        order.setDestinationLockerId(LOCKER);
        order.setReservedBoxId(BOX);
        mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setDroneCode(DRONE);
        mission.setStatus("APPROACHING");

        lenient().when(commLogRepository.save(any(DroneLockerCommLog.class))).thenAnswer(call -> {
            DroneLockerCommLog log = call.getArgument(0);
            if ("INBOUND".equals(log.getDirection()) && log.getMessageId() != null) {
                seenMessageIds.add(log.getMessageId());
            }
            return log;
        });
        lenient().when(commLogRepository.existsByMessageIdAndDirection(anyString(), eq("INBOUND")))
                .thenAnswer(call -> seenMessageIds.contains(call.<String>getArgument(0)));
        lenient().when(missionRepository.findFirstByDroneCodeAndStatusInOrderByIdDesc(eq(DRONE), any()))
                .thenAnswer(call -> DroneMissionProgressService.IN_FLIGHT_STAGES.contains(mission.getStatus())
                        ? Optional.of(mission) : Optional.empty());
        lenient().when(missionRepository.findByOrderId(21L)).thenAnswer(call -> Optional.of(mission));
        lenient().when(orderRepository.findByIdForUpdate(21L)).thenAnswer(call -> Optional.of(order));
        lenient().when(lockerDroneClient.getBoxCell(BOX)).thenReturn(ApiResponse.ok(
                new BoxCellDto(BOX, 1, "M", "DRONE", 0, 1, "RESERVED", null, false, "CLOSED", LOCKER,
                        "DICT_4X4_50", 7, null)));
        lenient().when(notificationClient.notifyGuest(any())).thenReturn(ApiResponse.ok(
                new GuestNotification.Result(false, true, false, true)));

        lenient().when(sessionRepository.save(any(DroneDeliverySession.class))).thenAnswer(call -> {
            DroneDeliverySession session = call.getArgument(0);
            sessions.put(session.getSessionId(), session);
            return session;
        });
        lenient().when(sessionRepository.findById(anyString()))
                .thenAnswer(call -> Optional.ofNullable(sessions.get(call.<String>getArgument(0))));
        lenient().when(sessionRepository.findByOpenCommandId(anyString())).thenAnswer(call -> sessions.values().stream()
                .filter(s -> call.<String>getArgument(0).equals(s.getOpenCommandId())).findFirst());
        lenient().when(sessionRepository.findByCloseCommandId(anyString())).thenAnswer(call -> sessions.values().stream()
                .filter(s -> call.<String>getArgument(0).equals(s.getCloseCommandId())).findFirst());
        lenient().when(sessionRepository.findFirstByOrderIdAndStateInOrderByCreatedAtDesc(anyLong(), anyCollection()))
                .thenAnswer(call -> sessions.values().stream()
                        .filter(s -> s.getOrderId().equals(call.getArgument(0))
                                && call.<java.util.Collection<String>>getArgument(1).contains(s.getState()))
                        .reduce((first, second) -> second));
        lenient().when(sessionRepository.findFirstByDroneIdAndStateOrderByCreatedAtDesc(anyString(), anyString()))
                .thenAnswer(call -> sessions.values().stream()
                        .filter(s -> s.getDroneId().equals(call.getArgument(0)) && s.getState().equals(call.getArgument(1)))
                        .reduce((first, second) -> second));
        lenient().when(sessionRepository.findByLockerIdAndStateIn(anyLong(), anyCollection()))
                .thenAnswer(call -> sessions.values().stream()
                        .filter(s -> s.getLockerId().equals(call.getArgument(0))
                                && call.<java.util.Collection<String>>getArgument(1).contains(s.getState()))
                        .toList());
        lenient().when(sessionRepository.findByStateInOrderByCreatedAtDesc(anyCollection()))
                .thenAnswer(call -> sessions.values().stream()
                        .filter(s -> call.<java.util.Collection<String>>getArgument(0).contains(s.getState()))
                        .toList());
    }

    // ------------------------------------------------------------------ chữ ký, độ mới, phát lại

    @Test
    void unsignedDroneEventIsRejectedAndChangesNothing() {
        service.handleIncomingMessage(DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, marker(null, null), null);

        assertEquals("APPROACHING", order.getDeliveryStage());
        assertTrue(sessions.isEmpty());
    }

    @Test
    void eventSignedWithAnotherDronesKeyIsRejected() {
        String body = marker(null, null);
        String forged = DroneTelemetrySigner.sign(DroneTelemetrySigner.deviceKey(SECRET, "DRONE-OTHER"),
                DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, body);

        service.handleIncomingMessage(DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, body, forged);

        assertEquals("APPROACHING", order.getDeliveryStage());
        assertTrue(sessions.isEmpty());
    }

    @Test
    void staleOrReplayedEventsAreRejected() {
        String old = event("MARKER_DETECTED", Map.of("arucoMarkerId", 7, "arucoDictionary", "DICT_4X4_50"),
                OffsetDateTime.now().minusMinutes(10));
        deliverSigned(old);
        assertTrue(sessions.isEmpty(), "bản tin cũ 10 phút bị xử lý");

        String fresh = marker(null, null);
        deliverSigned(fresh);
        assertEquals(1, sessions.size());
        sessions.clear();
        order.setDeliveryStage("APPROACHING");
        mission.setStatus("APPROACHING");

        deliverSigned(fresh); // phát lại đúng bản tin đã ký
        assertTrue(sessions.isEmpty(), "bản tin phát lại tạo phiên mới");
    }

    // ------------------------------------------------------------------ định danh

    @Test
    void markerBindsToTheSignedDronesOwnMissionWithoutGuessing() {
        deliverSigned(marker(null, null));

        assertEquals("ARRIVED", order.getDeliveryStage());
        DroneDeliverySession session = sessions.values().iterator().next();
        assertEquals(21L, session.getOrderId());
        assertEquals(LOCKER, session.getLockerId());
        assertEquals(BOX, session.getCompartmentId());
        assertEquals(DroneDeliverySession.OPEN_LID_REQUESTED, session.getState());
        assertNotNull(session.getOpenCommandId());
    }

    @Test
    void markerClaimingAnotherOrderOrLockerIsRejected() {
        deliverSigned(marker(88L, null));
        deliverSigned(marker(null, 99L));

        assertEquals("APPROACHING", order.getDeliveryStage());
        assertTrue(sessions.isEmpty());
    }

    @Test
    void droneWithoutAFlightIsRejected() {
        mission.setStatus("CANCELED");
        order.setStatus("CANCELED");
        order.setDeliveryStage("CANCELED");

        deliverSigned(marker(21L, LOCKER));

        assertEquals("CANCELED", order.getStatus());
        assertNull(order.getPinCode());
        assertTrue(sessions.isEmpty());
    }

    @Test
    void repeatedMarkerKeepsTheSameSessionAndDoesNotReopenTheLid() {
        deliverSigned(marker(null, null));
        String openCommand = sessions.values().iterator().next().getOpenCommandId();

        deliverSigned(marker(null, null));

        assertEquals(1, sessions.size());
        assertEquals(openCommand, sessions.values().iterator().next().getOpenCommandId());
    }

    // ------------------------------------------------------------------ kết quả nắp của tủ

    @Test
    void fullHandshakeEndsReadyForPickupOnlyAfterTheMatchingCloseResult() {
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();

        lockerResult(LOCKER, "open", session.getOpenCommandId(), "OPEN");
        assertEquals(DroneDeliverySession.LID_OPENED, session.getState());

        deliverSigned(event("DROP_PHASE_COMPLETED", Map.of("sessionId", session.getSessionId()), OffsetDateTime.now()));
        assertEquals(DroneDeliverySession.CLOSE_LID_REQUESTED, session.getState());
        assertEquals("AWAITING_DISPATCH", order.getStatus(), "chưa đóng nắp đã báo hàng vào ô");

        lockerResult(LOCKER, "close", session.getCloseCommandId(), "CLOSED");

        assertEquals("STORING", order.getStatus());
        assertEquals("READY_FOR_PICKUP", order.getDeliveryStage());
        assertNotNull(order.getPinCode());
        assertEquals(DroneDeliverySession.READY_FOR_PICKUP, session.getState());

        // Kết quả đóng nắp gửi lặp: không cấp PIN lần nữa.
        String pin = order.getPinCode();
        lockerResult(LOCKER, "close", session.getCloseCommandId(), "CLOSED");
        assertEquals(pin, order.getPinCode());
    }

    @Test
    void forgedOrMismatchedLockerResultsAreIgnored() {
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();
        lockerResult(LOCKER, "open", session.getOpenCommandId(), "OPEN");
        deliverSigned(event("DROP_PHASE_COMPLETED", Map.of(), OffsetDateTime.now()));

        lockerResult(LOCKER, "close", "forged-" + UUID.randomUUID(), "CLOSED");   // mã lệnh lạ
        lockerResult(6L, "close", session.getCloseCommandId(), "CLOSED");          // tủ khác
        lockerResult(LOCKER, "close", session.getOpenCommandId(), "CLOSED");       // mã của lệnh mở

        assertEquals("AWAITING_DISPATCH", order.getStatus());
        assertNull(order.getPinCode());
        assertEquals(DroneDeliverySession.CLOSE_LID_REQUESTED, session.getState());
    }

    @Test
    void closeResultForAnOrderCanceledMidSessionDoesNotReopenIt() {
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();
        lockerResult(LOCKER, "open", session.getOpenCommandId(), "OPEN");
        deliverSigned(event("DROP_PHASE_COMPLETED", Map.of(), OffsetDateTime.now()));
        // Admin đóng đơn "giao không hoàn tất" trong lúc nắp còn mở.
        order.setStatus("CANCELED");
        order.setDeliveryStage("FAILED");

        lockerResult(LOCKER, "close", session.getCloseCommandId(), "CLOSED");

        assertEquals("CANCELED", order.getStatus());
        assertNull(order.getPinCode());
        assertEquals(DroneDeliverySession.ABORTED, session.getState());
    }

    @Test
    void dropCompletionFromAnotherDroneIsRejected() {
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();
        lockerResult(LOCKER, "open", session.getOpenCommandId(), "OPEN");

        String other = event("DROP_PHASE_COMPLETED", Map.of("droneId", "DRONE-OTHER"), OffsetDateTime.now());
        service.handleIncomingMessage(DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, other,
                DroneTelemetrySigner.sign(DroneTelemetrySigner.deviceKey(SECRET, "DRONE-OTHER"),
                        DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, other));

        assertEquals(DroneDeliverySession.LID_OPENED, session.getState());
    }

    // ------------------------------------------------------------------ tủ mất kết nối giữa chừng

    @Test
    void openCommandLostWhileLockerWasOfflineIsResentWhenItReconnects() throws Exception {
        MqttClient mqtt = connectedMqtt();
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();
        session.setCreatedAt(LocalDateTime.now().minusSeconds(90));
        session.setUpdatedAt(LocalDateTime.now().minusSeconds(90));
        forgetLidAttempts();   // 90 s đã trôi qua kể từ lần gửi
        lockerLastSeen(LOCKER, LocalDateTime.now().minusMinutes(5));   // tủ mất kết nối lúc lệnh được gửi

        heartbeat(LOCKER);   // Pi kết nối lại broker, gửi heartbeat ngay
        heartbeat(LOCKER);   // heartbeat kế tiếp ngay sau: lệnh vừa gửi lại, chưa gửi nữa
        heartbeat(6L);       // tủ khác: không liên quan

        verify(mqtt, times(2)).publish(eq("cabinet/" + LOCKER + "/command/open"), any(MqttMessage.class));
        verify(mqtt, never()).publish(eq("cabinet/6/command/open"), any(MqttMessage.class));

        lockerResult(LOCKER, "open", session.getOpenCommandId(), "OPEN");
        assertEquals(DroneDeliverySession.LID_OPENED, session.getState());
    }

    @Test
    void lidCommandIsNotResentWhileTheLockerMayStillBeMovingTheLid() throws Exception {
        MqttClient mqtt = connectedMqtt();
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        heartbeat(LOCKER);

        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/open"), any(MqttMessage.class));
    }

    @Test
    void openRequestUnansweredTooLongEndsTheSessionSoANewDetectionCanRetry() throws Exception {
        MqttClient mqtt = connectedMqtt();
        deliverSigned(marker(null, null));
        DroneDeliverySession stale = sessions.values().iterator().next();
        stale.setCreatedAt(LocalDateTime.now().minusMinutes(11));
        stale.setUpdatedAt(LocalDateTime.now().minusMinutes(11));
        forgetLidAttempts();

        heartbeat(LOCKER);   // tủ quay lại quá muộn: không mở nắp cho drone đã đi

        assertEquals(DroneDeliverySession.LID_OPEN_FAILED, stale.getState());
        assertEquals("LOCKER_NO_RESPONSE", stale.getErrorCode());
        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/open"), any(MqttMessage.class));

        deliverSigned(marker(null, null));
        assertEquals(2, sessions.size(), "phiên hết hạn vẫn chặn lần nhận diện mới");
    }

    @Test
    void sweepExpiresOpenRequestsEvenIfTheLockerNeverComesBack() {
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();
        session.setCreatedAt(LocalDateTime.now().minusMinutes(11));

        service.expireStaleOpenRequests();

        assertEquals(DroneDeliverySession.LID_OPEN_FAILED, session.getState());
    }

    @Test
    void openLidMaxWaitFollowsTheAdminSetting() {
        OrderRules shortWait = TestOrderRules.of(Map.of(
                com.huynqb.laundrylocker.order.settings.OrderSettingsCatalog.DRONE_OPEN_LID_MAX_WAIT_SECONDS, 60));
        DroneMissionProgressService progress = new DroneMissionProgressService(missionRepository, orderRepository,
                historyRepository, notificationClient, lockerDroneClient, lockerClient, userClient, shortWait, mailer);
        service = new DroneLockerCommunicationService(commLogRepository, orderRepository, missionRepository, progress,
                lockerDroneClient, shortWait, new ObjectMapper(), sessionRepository, DIRECT);
        ReflectionTestUtils.setField(service, "secret", SECRET);
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();

        session.setCreatedAt(LocalDateTime.now().minusSeconds(30));
        service.expireStaleOpenRequests();
        assertEquals(DroneDeliverySession.OPEN_LID_REQUESTED, session.getState());

        session.setCreatedAt(LocalDateTime.now().minusSeconds(61));
        service.expireStaleOpenRequests();
        assertEquals(DroneDeliverySession.LID_OPEN_FAILED, session.getState());
    }

    private MqttClient connectedMqtt() {
        MqttClient mqtt = mock(MqttClient.class);
        lenient().when(mqtt.isConnected()).thenReturn(true);
        ReflectionTestUtils.setField(service, "mqttClient", mqtt);
        return mqtt;
    }

    private void heartbeat(long lockerId) {
        service.handleIncomingMessage("cabinet/" + lockerId + "/heartbeat", "{\"status\":\"online\"}", null);
    }

    // ------------------------------------------------------------------ demo ArUco trong nhà

    @Test
    void indoorDemoBindsOnlyASingleMatchingDemoOrderWhenEnabled() {
        mission.setStatus("CANCELED"); // drone chưa có nhiệm vụ bay
        order.setFulfillmentMode("DEMO");
        order.setDeliveryStage("PICKED_UP");
        lenient().when(missionRepository.findByOrderId(21L)).thenReturn(Optional.empty());
        lenient().when(orderRepository.findByTypeAndStatusNotInOrderByUpdatedAtDesc(eq("DRONE_DELIVERY"), any()))
                .thenReturn(List.of(order));
        lenient().when(missionRepository.save(any(DroneMission.class))).thenAnswer(call -> {
            DroneMission created = call.getArgument(0);
            created.setId(400L);
            mission = created;
            lenient().when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(created));
            return created;
        });
        DroneLockerCommunicationService demo = indoorService(true);

        String body = event("MARKER_DETECTED", Map.of("arucoMarkerId", 7, "arucoDictionary", "DICT_4X4_50",
                "mode", "INDOOR_ARUCO_DEMO"), OffsetDateTime.now());
        demo.handleIncomingMessage(DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, body, signed(body));

        assertEquals("ARRIVED", order.getDeliveryStage());
        assertEquals(DRONE, mission.getDroneCode());
        assertEquals(1, sessions.size());
    }

    @Test
    void indoorDemoIsRejectedWhenDisabledOrAmbiguous() {
        mission.setStatus("CANCELED");
        order.setFulfillmentMode("DEMO");
        LockerOrder twin = new LockerOrder();
        twin.setId(22L);
        twin.setType("DRONE_DELIVERY");
        twin.setFulfillmentMode("DEMO");
        twin.setStatus("AWAITING_DISPATCH");
        twin.setLockerId(LOCKER);
        twin.setReservedBoxId(BOX);
        lenient().when(missionRepository.findByOrderId(anyLong())).thenReturn(Optional.empty());
        lenient().when(orderRepository.findByTypeAndStatusNotInOrderByUpdatedAtDesc(eq("DRONE_DELIVERY"), any()))
                .thenReturn(List.of(order, twin));

        String body = event("MARKER_DETECTED", Map.of("arucoMarkerId", 7, "arucoDictionary", "DICT_4X4_50",
                "mode", "INDOOR_ARUCO_DEMO"), OffsetDateTime.now());
        indoorService(true).handleIncomingMessage(DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, body, signed(body));
        assertTrue(sessions.isEmpty(), "hai đơn cùng mã ô mà vẫn gắn bừa một đơn");

        String again = event("MARKER_DETECTED", Map.of("arucoMarkerId", 7, "arucoDictionary", "DICT_4X4_50",
                "mode", "INDOOR_ARUCO_DEMO"), OffsetDateTime.now());
        lenient().when(orderRepository.findByTypeAndStatusNotInOrderByUpdatedAtDesc(eq("DRONE_DELIVERY"), any()))
                .thenReturn(List.of(order));
        indoorService(false).handleIncomingMessage(DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, again, signed(again));
        assertTrue(sessions.isEmpty(), "chế độ demo trong nhà đang tắt mà vẫn tạo nhiệm vụ");
    }

    @Test
    void indoorModeAloneDoesNotLetTheCameraSkipTheTechnicianSteps() {
        mission.setStatus("CANCELED"); // KTV chưa lấy hàng, chưa nạp, chưa phóng
        order.setFulfillmentMode("DEMO");
        lenient().when(missionRepository.findByOrderId(anyLong())).thenReturn(Optional.empty());
        lenient().when(orderRepository.findByTypeAndStatusNotInOrderByUpdatedAtDesc(eq("DRONE_DELIVERY"), any()))
                .thenReturn(List.of(order));

        String body = event("MARKER_DETECTED", Map.of("arucoMarkerId", 7, "arucoDictionary", "DICT_4X4_50",
                "mode", "INDOOR_ARUCO_DEMO"), OffsetDateTime.now());
        indoorService(true, false).handleIncomingMessage(
                DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, body, signed(body));

        assertTrue(sessions.isEmpty(), "camera tự tạo nhiệm vụ khi chưa bật demo drone cầm tay");
        verify(missionRepository, never()).save(any(DroneMission.class));
    }

    private DroneLockerCommunicationService indoorService(boolean enabled) {
        return indoorService(enabled, enabled);
    }

    /// `handheld`: cho phép camera tự tạo nhiệm vụ cho đơn DEMO chưa được KTV phóng (mặc định tắt).
    private DroneLockerCommunicationService indoorService(boolean enabled, boolean handheld) {
        OrderRules demoRules = TestOrderRules.of(Map.of(
                com.huynqb.laundrylocker.order.settings.OrderSettingsCatalog.DRONE_DEMO_INDOOR_ARUCO_ENABLED, enabled,
                com.huynqb.laundrylocker.order.settings.OrderSettingsCatalog.DRONE_DEMO_INDOOR_HANDHELD_ENABLED, handheld));
        DroneMissionProgressService progress = new DroneMissionProgressService(missionRepository, orderRepository,
                historyRepository, notificationClient, lockerDroneClient, lockerClient, userClient, demoRules, mailer);
        DroneLockerCommunicationService demo = new DroneLockerCommunicationService(commLogRepository, orderRepository,
                missionRepository, progress, lockerDroneClient, demoRules, new ObjectMapper(), sessionRepository, DIRECT);
        ReflectionTestUtils.setField(demo, "secret", SECRET);
        return demo;
    }

    private static String signed(String body) {
        return DroneTelemetrySigner.sign(DroneTelemetrySigner.deviceKey(SECRET, DRONE),
                DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, body);
    }

    // ------------------------------------------------------------------ lệnh gửi drone

    @Test
    void commandsToTheDroneAreSignedWithThatDronesKey() {
        String body = service.commandPayload("LID_OPENED", "S1", "C1", 21L, DRONE, LOCKER, BOX, null, null);

        String signature = service.signCommand(DRONE, body);

        assertTrue(DroneTelemetrySigner.verify(SECRET, DRONE, DroneLockerCommunicationService.DRONE_COMMANDS_TOPIC,
                body, signature));
        assertNull(service.signCommand(null, body));
    }

    // ------------------------------------------------------------------ mở nắp thật theo ArUco

    /// Đúng mã, đúng nhiệm vụ ⇒ đúng một lệnh mở NẮP TRƯỢT (lid:true) tới đúng tủ, có commandId, có hạn, không retained.
    @Test
    void rightMarkerForTheMissionSendsExactlyOneExpiringSlidingLidOpen() throws Exception {
        MqttClient mqtt = connectedMqtt();

        deliverSigned(marker(null, null));
        deliverSigned(marker(null, null));   // drone gửi lặp: không mở lần hai

        ArgumentCaptor<MqttMessage> sent = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/open"), sent.capture());
        verify(mqtt, never()).publish(eq("cabinet/" + LOCKER + "/command/close"), any(MqttMessage.class));
        var payload = new ObjectMapper().readTree(new String(sent.getValue().getPayload()));
        DroneDeliverySession session = sessions.values().iterator().next();
        assertEquals(session.getOpenCommandId(), payload.get("commandId").asText());
        assertTrue(payload.get("lid").asBoolean(), "phải là nắp trượt, không phải relay cửa ô");
        assertEquals(BOX, payload.get("boxId").asLong());
        assertEquals("DRONE_DELIVERY", payload.get("source").asText());
        assertEquals(service.openCommandExpiresAt(session), payload.get("expiresAt").asText());
        assertFalse(sent.getValue().isRetained());
    }

    /// Camera thấy mã của ô khác ⇒ không gửi gì tới tủ.
    @Test
    void wrongMarkerSendsNothingToTheLocker() throws Exception {
        MqttClient mqtt = connectedMqtt();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("arucoMarkerId", 8);
        fields.put("arucoDictionary", "DICT_4X4_50");
        fields.put("mode", "REAL_OUTDOOR");

        deliverSigned(event("MARKER_DETECTED", fields, OffsetDateTime.now()));

        verify(mqtt, never()).publish(eq("cabinet/" + LOCKER + "/command/open"), any(MqttMessage.class));
        assertTrue(sessions.isEmpty());
    }

    /// Đang cất cánh ở tủ gửi (chưa tới chặng tiếp cận) ⇒ không mở nắp dù mã đúng.
    @Test
    void markerBeforeTheApproachStagesIsNotAllowedToOpenTheLid() throws Exception {
        MqttClient mqtt = connectedMqtt();
        mission.setStatus("DEPARTED");
        order.setDeliveryStage("DEPARTED");

        deliverSigned(marker(null, null));

        verify(mqtt, never()).publish(eq("cabinet/" + LOCKER + "/command/open"), any(MqttMessage.class));
        assertTrue(sessions.isEmpty());
    }

    /// "Đã gửi lệnh" → "tủ đã nhận lệnh" (ACCEPTED, nắp đang chạy) → "nắp đã mở" là ba trạng thái khác nhau.
    @Test
    void acceptedIsOnlyAcknowledgedUntilTheLockerReportsTheLidOpen() {
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();
        assertEquals(DroneDeliverySession.OPEN_LID_REQUESTED, session.getState());

        lockerReply(LOCKER, "open", session.getOpenCommandId(), "ACCEPTED", "MOVING", "");
        assertEquals(DroneDeliverySession.LID_OPENING, session.getState());
        assertNull(session.getLidOpenedAt(), "tủ mới nhận lệnh, nắp chưa mở");

        lockerReply(LOCKER, "open", session.getOpenCommandId(), "ACCEPTED", "MOVING", ",\"duplicate\":true");
        assertEquals(DroneDeliverySession.LID_OPENING, session.getState());

        lockerReply(LOCKER, "open", session.getOpenCommandId(), "SUCCESS", "OPEN",
                ",\"lidConfirmation\":\"LIMIT_SWITCH\"");
        assertEquals(DroneDeliverySession.LID_OPENED, session.getState());
        assertNotNull(session.getLidOpenedAt());
    }

    /// Chưa có ACK: gửi lại ĐÚNG commandId cũ, có giới hạn; hết lượt thì báo lỗi, không treo phiên.
    @Test
    void unacknowledgedOpenIsRetriedWithTheSameCommandIdThenFails() throws Exception {
        MqttClient mqtt = connectedMqtt();
        settings.update(Map.of("app.drone.delivery.lid-ack-timeout-seconds", 0), null);
        settings.update(Map.of("app.drone.delivery.lid-max-retries", 2), null);
        heartbeat(LOCKER);
        markLockerAckCapable();
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();

        service.retryStalledLidCommands();
        service.retryStalledLidCommands();
        assertEquals(DroneDeliverySession.OPEN_LID_REQUESTED, session.getState());
        service.retryStalledLidCommands();   // hết 2 lượt gửi lại

        ArgumentCaptor<MqttMessage> sent = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqtt, times(3)).publish(eq("cabinet/" + LOCKER + "/command/open"), sent.capture());
        for (MqttMessage message : sent.getAllValues()) {
            assertEquals(session.getOpenCommandId(),
                    new ObjectMapper().readTree(new String(message.getPayload())).get("commandId").asText());
        }
        assertEquals(DroneDeliverySession.LID_OPEN_FAILED, session.getState());
        assertEquals("LOCKER_NO_ACK", session.getErrorCode());
    }

    /// Tủ mất kết nối: không đốt lượt gửi lại, chờ heartbeat lúc tủ quay lại.
    @Test
    void offlineLockerDoesNotBurnRetries() throws Exception {
        MqttClient mqtt = connectedMqtt();
        settings.update(Map.of("app.drone.delivery.lid-ack-timeout-seconds", 0), null);
        settings.update(Map.of("app.drone.delivery.lid-max-retries", 1), null);
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();

        service.retryStalledLidCommands();
        service.retryStalledLidCommands();

        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/open"), any(MqttMessage.class));
        assertEquals(DroneDeliverySession.OPEN_LID_REQUESTED, session.getState());
    }

    /// Tủ đã nhận lệnh và đang chạy nắp: không gửi lại theo hạn ACK (chờ theo thời gian chạy nắp).
    @Test
    void lidThatIsMovingIsNotNaggedWithRetries() throws Exception {
        MqttClient mqtt = connectedMqtt();
        settings.update(Map.of("app.drone.delivery.lid-ack-timeout-seconds", 0), null);
        heartbeat(LOCKER);
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();
        lockerReply(LOCKER, "open", session.getOpenCommandId(), "ACCEPTED", "MOVING", "");

        service.retryStalledLidCommands();

        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/open"), any(MqttMessage.class));
    }

    /// Tủ bỏ lệnh quá hạn / báo lỗi ⇒ phiên ghi đúng lỗi; drone được báo để không thả hàng.
    @Test
    void lockerErrorsAndExpiredCommandsEndTheSessionWithTheirCode() {
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();

        lockerReply(LOCKER, "open", session.getOpenCommandId(), "FAILED", "CLOSED", ",\"errorCode\":\"COMMAND_EXPIRED\"");

        assertEquals(DroneDeliverySession.LID_OPEN_FAILED, session.getState());
        assertEquals("COMMAND_EXPIRED", session.getErrorCode());
    }

    /// Pi tủ bật chế độ giả lập (không trượt nắp) ⇒ không được coi là đã mở.
    @Test
    void simulatedLidOpenIsNotAcceptedAsARealOpen() {
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();

        lockerReply(LOCKER, "open", session.getOpenCommandId(), "SUCCESS", "OPEN", ",\"lidConfirmation\":\"BYPASS\"");

        assertEquals(DroneDeliverySession.LID_OPEN_FAILED, session.getState());
        assertEquals("LID_SIMULATED", session.getErrorCode());
    }

    /// Tủ chưa có công tắc hành trình: vẫn mở được nhưng ghi rõ "chưa xác nhận mở hoàn toàn".
    @Test
    void openWithoutLimitSwitchIsReportedAsUnconfirmed() {
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();
        ArgumentCaptor<DroneLockerCommLog> logs = ArgumentCaptor.forClass(DroneLockerCommLog.class);

        lockerReply(LOCKER, "open", session.getOpenCommandId(), "SUCCESS", "OPEN", ",\"lidConfirmation\":\"STEP_COUNT\"");

        assertEquals(DroneDeliverySession.LID_OPENED, session.getState());
        verify(commLogRepository, org.mockito.Mockito.atLeastOnce()).save(logs.capture());
        assertTrue(logs.getAllValues().stream().anyMatch(log -> "LID_OPENED".equals(log.getEventType())
                && log.getErrorMessage() != null && log.getErrorMessage().contains("chưa có công tắc hành trình")));
    }

    /// "Đã mở" không kèm xác nhận phần cứng (trình giả lập tủ trên broker chung, Pi chưa cập nhật) không được
    /// coi là nắp đã mở; phiên vẫn chờ kết quả thật của Pi tủ.
    @Test
    void openResultWithoutHardwareConfirmationIsIgnoredUntilTheRealLockerAnswers() {
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();

        lockerReply(LOCKER, "open", session.getOpenCommandId(), "SUCCESS", "OPEN", "");   // giả lập trả lời trong 1 s

        assertEquals(DroneDeliverySession.OPEN_LID_REQUESTED, session.getState());
        assertNull(session.getLidOpenedAt());

        lockerReply(LOCKER, "open", session.getOpenCommandId(), "ACCEPTED", "MOVING", "");
        lockerReply(LOCKER, "open", session.getOpenCommandId(), "SUCCESS", "OPEN", ",\"lidConfirmation\":\"STEP_COUNT\"");
        assertEquals(DroneDeliverySession.LID_OPENED, session.getState());
    }

    /// Pi tủ còn code cũ (không chống chạy trùng): sau một kết quả không xác nhận thì không gửi lại lệnh mở nữa
    /// — gửi lại là motor chạy lại. Hết thời gian chạy nắp thì báo LID_UNVERIFIED.
    @Test
    void unverifiedOpenIsNeverResentSoALegacyLockerDoesNotRunTheMotorAgain() throws Exception {
        MqttClient mqtt = connectedMqtt();
        settings.update(Map.of("app.drone.delivery.lid-ack-timeout-seconds", 0), null);
        settings.update(Map.of("app.drone.delivery.lid-motion-timeout-seconds", 0), null);
        heartbeat(LOCKER);
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();

        lockerReply(LOCKER, "open", session.getOpenCommandId(), "SUCCESS", "OPEN", "");
        service.retryStalledLidCommands();
        service.retryStalledLidCommands();

        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/open"), any(MqttMessage.class));
        assertEquals(DroneDeliverySession.LID_OPEN_FAILED, session.getState());
        assertEquals("LID_UNVERIFIED", session.getErrorCode());
    }

    /// Demo trong nhà: lệnh LID_OPENED gửi drone mang số giây giả lập nhả hàng theo cấu hình admin.
    @Test
    void demoLidOpenedCarriesTheDropSimulationSeconds() {
        String body = service.commandPayload("LID_OPENED", "S1", "C1", 21L, DRONE, LOCKER, BOX, null, "ok",
                Map.of("dropSimulationSeconds", 9, "lidConfirmation", "STEP_COUNT"));
        assertTrue(body.contains("\"dropSimulationSeconds\":9"));
        assertTrue(body.contains("\"lidConfirmation\":\"STEP_COUNT\""));
    }

    private String demoMarker() {
        return event("MARKER_DETECTED", Map.of("arucoMarkerId", 7, "arucoDictionary", "DICT_4X4_50",
                "mode", "INDOOR_ARUCO_DEMO", "sessionId", "session-demo-1"), OffsetDateTime.now());
    }

    private List<com.fasterxml.jackson.databind.JsonNode> droneCommands(MqttClient mqtt) throws Exception {
        ArgumentCaptor<MqttMessage> sent = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqtt, org.mockito.Mockito.atLeast(0))
                .publish(eq(DroneLockerCommunicationService.DRONE_COMMANDS_TOPIC), sent.capture());
        List<com.fasterxml.jackson.databind.JsonNode> commands = new java.util.ArrayList<>();
        for (MqttMessage message : sent.getAllValues()) {
            commands.add(new ObjectMapper().readTree(new String(message.getPayload())));
        }
        return commands;
    }

    /// Demo trong nhà: tủ mở nắp thật xong ⇒ gửi lệnh nhả hàng (dropSimulationSeconds) cho Pi drone và CHỜ drone báo
    /// nhả xong — backend không tự hẹn giờ đóng nắp. Drone báo xong ⇒ gửi lệnh đóng nắp và báo drone LID_CLOSING.
    @Test
    void demoLidOpenAsksTheDroneToDropAndClosesOnlyAfterItsReport() throws Exception {
        MqttClient mqtt = connectedMqtt();
        deliverSigned(demoMarker());
        DroneDeliverySession session = sessions.values().iterator().next();
        assertEquals("INDOOR_ARUCO_DEMO", session.getMode());

        lockerResult(LOCKER, "open", session.getOpenCommandId(), "OPEN");

        assertEquals(DroneDeliverySession.LID_OPENED, session.getState());
        // Dòng "đã gửi lệnh mở" / "tủ đã nhận lệnh" cùng mã lệnh thôi "đang xử lý".
        verify(commLogRepository).resolvePendingByMessageId(session.getOpenCommandId(), "SUCCESS");
        var lidOpened = droneCommands(mqtt).stream()
                .filter(cmd -> "LID_OPENED".equals(cmd.get("eventType").asText())).findFirst().orElseThrow();
        assertEquals(7, lidOpened.get("dropSimulationSeconds").asInt(), "lệnh nhả hàng theo cấu hình admin");
        assertTrue(lidOpened.get("parcelRelease").asBoolean(), "Pi có servo phải nhận được lệnh nhả hàng");
        assertEquals(session.getSessionId(), lidOpened.get("sessionId").asText());
        verify(mqtt, never()).publish(eq("cabinet/" + LOCKER + "/command/close"), any(MqttMessage.class));
        ArgumentCaptor<DroneLockerCommLog> logs = ArgumentCaptor.forClass(DroneLockerCommLog.class);
        verify(commLogRepository, org.mockito.Mockito.atLeastOnce()).save(logs.capture());
        assertTrue(logs.getAllValues().stream().anyMatch(log -> "DROP_PHASE_STARTED".equals(log.getEventType())
                && "BACKEND".equals(log.getSourceDevice()) && "DRONE".equals(log.getDestinationDevice())));

        service.expireDropReports();   // chưa quá hạn: vẫn chờ drone
        assertEquals(DroneDeliverySession.LID_OPENED, session.getState());

        deliverSigned(event("DROP_PHASE_COMPLETED", Map.of("sessionId", session.getSessionId()), OffsetDateTime.now()));

        assertEquals(DroneDeliverySession.CLOSE_LID_REQUESTED, session.getState());
        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/close"), any(MqttMessage.class));
        assertTrue(droneCommands(mqtt).stream().anyMatch(cmd -> "LID_CLOSING".equals(cmd.get("eventType").asText())));

        // Drone gửi lại báo cáo (chưa nhận LID_CLOSING): không đóng nắp lần nữa, không ghi thành lỗi.
        deliverSigned(event("DROP_PHASE_COMPLETED", Map.of("sessionId", session.getSessionId()), OffsetDateTime.now()));
        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/close"), any(MqttMessage.class));
        verify(commLogRepository, org.mockito.Mockito.atLeastOnce()).save(logs.capture());
        assertTrue(logs.getAllValues().stream().noneMatch(log -> "NO_SESSION_WAITING_DROP".equals(log.getErrorCode())));

        lockerResult(LOCKER, "close", session.getCloseCommandId(), "CLOSED");
        assertEquals(DroneDeliverySession.READY_FOR_PICKUP, session.getState());
        verify(commLogRepository).resolvePendingByMessageId(session.getCloseCommandId(), "SUCCESS");
    }

    /// Mỗi bước chỉ chạy khi bước trước thành công: drone chưa báo nhả hàng xong thì KHÔNG đóng nắp, dù quá hạn —
    /// chỉ ghi cảnh báo một lần và tiếp tục chờ; drone báo muộn thì luồng đi tiếp bình thường.
    @Test
    void overdueDropReportIsOnlyWarnedAndTheLidIsNotClosedWithoutIt() throws Exception {
        MqttClient mqtt = connectedMqtt();
        deliverSigned(demoMarker());
        DroneDeliverySession session = sessions.values().iterator().next();
        lockerResult(LOCKER, "open", session.getOpenCommandId(), "OPEN");
        session.setLidOpenedAt(LocalDateTime.now().minusSeconds(7 + DroneLockerCommunicationService.DROP_REPORT_GRACE_SECONDS + 1));

        service.expireDropReports();
        service.expireDropReports();

        assertEquals(DroneDeliverySession.LID_OPENED, session.getState(), "tự đóng nắp khi drone chưa nhả hàng xong");
        verify(mqtt, never()).publish(eq("cabinet/" + LOCKER + "/command/close"), any(MqttMessage.class));
        ArgumentCaptor<DroneLockerCommLog> logs = ArgumentCaptor.forClass(DroneLockerCommLog.class);
        verify(commLogRepository, org.mockito.Mockito.atLeastOnce()).save(logs.capture());
        assertEquals(1, logs.getAllValues().stream().filter(log -> "DROP_REPORT_TIMEOUT".equals(log.getEventType())).count(),
                "cảnh báo quá hạn phải ghi đúng một lần");

        deliverSigned(event("DROP_PHASE_COMPLETED", Map.of("sessionId", session.getSessionId()), OffsetDateTime.now()));
        assertEquals(DroneDeliverySession.CLOSE_LID_REQUESTED, session.getState());
        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/close"), any(MqttMessage.class));
        verify(commLogRepository).resolvePendingByMessageId(
                DroneLockerCommunicationService.dropCommandLogId(session.getSessionId()), "SUCCESS");
    }

    /// Pi drone khởi động lại khi nắp đang mở chờ thả: mục tiêu trả về nhắc lại lệnh nhả hàng và mã phiên.
    @Test
    void targetDuringAnOpenDemoLidRepeatsTheDropCommand() throws Exception {
        MqttClient mqtt = connectedMqtt();
        lenient().when(orderRepository.findById(21L)).thenAnswer(call -> Optional.of(order));
        deliverSigned(demoMarker());
        DroneDeliverySession session = sessions.values().iterator().next();
        lockerResult(LOCKER, "open", session.getOpenCommandId(), "OPEN");

        deliverSigned(event("TARGET_REQUEST", Map.of(), OffsetDateTime.now()));

        var target = droneCommands(mqtt).stream()
                .filter(cmd -> "DELIVERY_TARGET".equals(cmd.get("eventType").asText())).findFirst().orElseThrow();
        assertEquals("LID_OPENED", target.get("sessionState").asText());
        assertEquals("INDOOR_ARUCO_DEMO", target.get("mode").asText());
        assertEquals(7, target.get("dropSimulationSeconds").asInt());
        assertTrue(target.get("parcelRelease").asBoolean());
        assertEquals(session.getSessionId(), target.get("sessionId").asText());
    }

    // ------------------------------------------------------------------ nhả hàng bằng servo

    /// Giao thật: nắp mở ⇒ lệnh nhả hàng (`parcelRelease`) cho drone, không có số giây giả lập; servo báo nhả xong
    /// ⇒ đóng nắp ⇒ đơn sẵn sàng lấy hàng.
    @Test
    void realDeliveryAsksTheDroneServoToReleaseThenClosesTheLid() throws Exception {
        MqttClient mqtt = connectedMqtt();
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();
        assertEquals("REAL_OUTDOOR", session.getMode());

        lockerResult(LOCKER, "open", session.getOpenCommandId(), "OPEN");

        var lidOpened = droneCommands(mqtt).stream()
                .filter(cmd -> "LID_OPENED".equals(cmd.get("eventType").asText())).findFirst().orElseThrow();
        assertTrue(lidOpened.get("parcelRelease").asBoolean(), "giao thật không có lệnh nhả hàng");
        assertFalse(lidOpened.has("dropSimulationSeconds"), "giao thật không được giả lập nhả hàng");
        verify(mqtt, never()).publish(eq("cabinet/" + LOCKER + "/command/close"), any(MqttMessage.class));
        ArgumentCaptor<DroneLockerCommLog> logs = ArgumentCaptor.forClass(DroneLockerCommLog.class);
        verify(commLogRepository, org.mockito.Mockito.atLeastOnce()).save(logs.capture());
        assertTrue(logs.getAllValues().stream().anyMatch(log -> "DROP_PHASE_STARTED".equals(log.getEventType())
                && DroneLockerCommunicationService.dropCommandLogId(session.getSessionId()).equals(log.getMessageId())));

        deliverSigned(event("DROP_PHASE_COMPLETED", Map.of("sessionId", session.getSessionId(),
                "releaseMethod", "SERVO", "releaseAttempts", 1, "servoOpenAngle", 180.0, "servoHoldSeconds", 5.0,
                "releaseDurationSeconds", 6.6, "servoGpioPin", 18, "gpioBackend", "lgpio"), OffsetDateTime.now()));

        assertEquals(DroneDeliverySession.CLOSE_LID_REQUESTED, session.getState());
        // Nhật ký ghi số liệu thật của servo (giống MARKER_DETECTED ghi khoảng cách / độ lệch).
        verify(commLogRepository, org.mockito.Mockito.atLeastOnce()).save(logs.capture());
        assertTrue(logs.getAllValues().stream().anyMatch(log -> "DROP_PHASE_COMPLETED".equals(log.getEventType())
                && "SUCCESS".equals(log.getProcessingStatus())
                && log.getErrorMessage().contains("servo mở 180° · giữ 5 giây · 1 lần thử · chạy 6.6 giây · GPIO18 (lgpio)")),
                "nhật ký nhả hàng thiếu số liệu servo");
        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/close"), any(MqttMessage.class));
        verify(commLogRepository).resolvePendingByMessageId(
                DroneLockerCommunicationService.dropCommandLogId(session.getSessionId()), "SUCCESS");

        lockerResult(LOCKER, "close", session.getCloseCommandId(), "CLOSED");
        assertEquals(DroneDeliverySession.READY_FOR_PICKUP, session.getState());
        assertEquals("STORING", order.getStatus());
        assertNotNull(order.getPinCode());
    }

    /// Servo lỗi: đóng nắp cho an toàn nhưng KHÔNG coi là hàng đã vào ô; đơn không được tự giao lại.
    @Test
    void servoFailureClosesTheLidWithoutDeliveringTheOrder() throws Exception {
        MqttClient mqtt = connectedMqtt();
        lenient().when(orderRepository.findById(21L)).thenAnswer(call -> Optional.of(order));
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();
        lockerResult(LOCKER, "open", session.getOpenCommandId(), "OPEN");

        String failure = event("PARCEL_RELEASE_FAILED", Map.of("sessionId", session.getSessionId(),
                "errorCode", "SERVO_ERROR", "errorMessage", "RuntimeError: GPIO", "releaseAttempts", 2),
                OffsetDateTime.now());
        deliverSigned(failure);

        assertEquals(DroneDeliverySession.CLOSE_LID_REQUESTED, session.getState());
        assertEquals("PARCEL_RELEASE_FAILED", session.getErrorCode());
        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/close"), any(MqttMessage.class));
        verify(commLogRepository).resolvePendingByMessageId(
                DroneLockerCommunicationService.dropCommandLogId(session.getSessionId()), "FAILED");
        assertTrue(droneCommands(mqtt).stream().anyMatch(cmd -> "LID_CLOSING".equals(cmd.get("eventType").asText())));

        // Pi gửi lại báo lỗi (chưa nhận LID_CLOSING): không đóng nắp lần nữa.
        deliverSigned(event("PARCEL_RELEASE_FAILED", Map.of("sessionId", session.getSessionId(),
                "errorCode", "SERVO_ERROR"), OffsetDateTime.now()));
        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/close"), any(MqttMessage.class));

        lockerResult(LOCKER, "close", session.getCloseCommandId(), "CLOSED");

        assertEquals(DroneDeliverySession.PARCEL_RELEASE_FAILED, session.getState());
        assertEquals("AWAITING_DISPATCH", order.getStatus(), "servo lỗi mà đơn được coi là đã vào ô");
        assertEquals("ARRIVED", order.getDeliveryStage());
        assertNull(order.getPinCode());
        assertTrue(droneCommands(mqtt).stream()
                .anyMatch(cmd -> "PARCEL_RELEASE_FAILED".equals(cmd.get("eventType").asText())));

        // Không tự giao lại: drone thấy lại mã ⇒ từ chối, hỏi mục tiêu ⇒ NO_TARGET.
        deliverSigned(marker(null, null));
        assertEquals(1, sessions.size(), "servo lỗi mà vẫn mở nắp lần nữa");
        deliverSigned(event("TARGET_REQUEST", Map.of(), OffsetDateTime.now()));
        var noTarget = droneCommands(mqtt).stream()
                .filter(cmd -> "NO_TARGET".equals(cmd.get("eventType").asText())).reduce((a, b) -> b).orElseThrow();
        assertEquals(DroneLockerCommunicationService.PARCEL_RELEASE_FAILED_MESSAGE, noTarget.get("errorMessage").asText());
    }

    @Test
    void releaseFailureWithoutAnOpenLidIsRejected() {
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();

        deliverSigned(event("PARCEL_RELEASE_FAILED", Map.of("sessionId", session.getSessionId(),
                "errorCode", "SERVO_ERROR"), OffsetDateTime.now()));

        assertEquals(DroneDeliverySession.OPEN_LID_REQUESTED, session.getState());
        assertNull(session.getCloseCommandId());
    }

    /// Drone chưa báo kết quả nhả hàng quá hạn: giao thật cũng được cảnh báo (trước chỉ đơn DEMO).
    @Test
    void overdueReleaseReportIsWarnedForRealDeliveriesToo() {
        deliverSigned(marker(null, null));
        DroneDeliverySession session = sessions.values().iterator().next();
        lockerResult(LOCKER, "open", session.getOpenCommandId(), "OPEN");
        session.setLidOpenedAt(LocalDateTime.now().minusSeconds(7 + DroneLockerCommunicationService.DROP_REPORT_GRACE_SECONDS + 1));

        service.expireDropReports();

        assertEquals(DroneDeliverySession.LID_OPENED, session.getState());
        ArgumentCaptor<DroneLockerCommLog> logs = ArgumentCaptor.forClass(DroneLockerCommLog.class);
        verify(commLogRepository, org.mockito.Mockito.atLeastOnce()).save(logs.capture());
        assertEquals(1, logs.getAllValues().stream().filter(log -> "DROP_REPORT_TIMEOUT".equals(log.getEventType())).count());
    }

    @Test
    void releaseSummaryListsOnlyTheFieldsTheDroneSent() throws Exception {
        ObjectMapper json = new ObjectMapper();
        assertEquals("servo mở 180° · giữ 5 giây · 2 lần thử · chạy 0.15 giây · GPIO18",
                DroneLockerCommunicationService.releaseSummary(json.readTree(
                        "{\"servoOpenAngle\":180.0,\"servoHoldSeconds\":5,\"releaseAttempts\":2,"
                                + "\"releaseDurationSeconds\":0.150,\"servoGpioPin\":18,\"gpioBackend\":null}")));
        assertEquals("", DroneLockerCommunicationService.releaseSummary(json.readTree("{\"releaseMethod\":\"SIMULATION\"}")));
    }

    @Test
    void releaseCommandDependsOnTheSessionMode() {
        assertEquals(Map.of("parcelRelease", true), service.parcelReleaseCommand("REAL_OUTDOOR"));
        assertEquals(Map.of("parcelRelease", true, "dropSimulationSeconds", 7),
                service.parcelReleaseCommand("INDOOR_ARUCO_DEMO"));
        assertTrue(service.parcelReleaseCommand(DroneDeliverySession.MODE_LID_TEST).isEmpty(),
                "thử nắp không có kiện để nhả");
    }

    // ------------------------------------------------------------------ chế độ thử nắp theo ArUco (không cần đơn)

    private void enableLidTest() {
        settings.update(Map.of("app.drone.lid-test.enabled", true, "app.drone.lid-test.locker-id", (int) LOCKER,
                "app.drone.lid-test.markers", "7:" + BOX), null);
    }

    private String lidTestMarker(int marker) {
        return event("MARKER_DETECTED", Map.of("arucoMarkerId", marker, "arucoDictionary", "DICT_4X4_50",
                "mode", "LID_TEST", "sessionId", "session-test-1"), OffsetDateTime.now());
    }

    /// Camera thấy mã trong danh sách thử ⇒ hệ thống trả mục tiêu thử (không có đơn) cho đúng mã đang thấy.
    @Test
    void lidTestTargetFollowsTheMarkerTheCameraSees() throws Exception {
        MqttClient mqtt = connectedMqtt();
        enableLidTest();

        deliverSigned(event("TARGET_REQUEST", Map.of("visibleMarkers", List.of(3, 7)), OffsetDateTime.now()));

        var target = droneCommands(mqtt).stream()
                .filter(cmd -> "DELIVERY_TARGET".equals(cmd.get("eventType").asText())).findFirst().orElseThrow();
        assertEquals("LID_TEST", target.get("mode").asText());
        assertEquals(7, target.get("arucoMarkerId").asInt());
        assertEquals(BOX, target.get("compartmentId").asLong());
        assertEquals(LOCKER, target.get("lockerId").asLong());
        assertFalse(target.has("orderId"), "mục tiêu thử nắp không gắn đơn");
    }

    /// Đúng mã ⇒ mở nắp THẬT; chỉ khi tủ báo mở xong mới giữ rồi đóng; tủ báo đóng xong ⇒ phiên thử xong.
    /// Không đơn nào bị đổi trạng thái.
    @Test
    void lidTestOpensHoldsAndClosesWithoutTouchingAnyOrder() throws Exception {
        MqttClient mqtt = connectedMqtt();
        enableLidTest();

        deliverSigned(lidTestMarker(7));

        DroneDeliverySession session = sessions.values().iterator().next();
        assertEquals("LID_TEST", session.getMode());
        assertEquals(0L, session.getOrderId());
        assertEquals(DroneDeliverySession.OPEN_LID_REQUESTED, session.getState());
        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/open"), any(MqttMessage.class));

        service.closeLidTest(session.getSessionId());     // chưa mở xong: không được đóng
        verify(mqtt, never()).publish(eq("cabinet/" + LOCKER + "/command/close"), any(MqttMessage.class));

        lockerResult(LOCKER, "open", session.getOpenCommandId(), "OPEN");
        assertEquals(DroneDeliverySession.LID_OPENED, session.getState());

        service.closeLidTest(session.getSessionId());     // hết thời gian giữ
        assertEquals(DroneDeliverySession.CLOSE_LID_REQUESTED, session.getState());
        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/close"), any(MqttMessage.class));

        lockerResult(LOCKER, "close", session.getCloseCommandId(), "CLOSED");
        assertEquals(DroneDeliverySession.TEST_COMPLETED, session.getState());
        assertTrue(droneCommands(mqtt).stream().anyMatch(cmd -> "LID_TEST_DONE".equals(cmd.get("eventType").asText())));
        assertEquals("AWAITING_DISPATCH", order.getStatus());
        assertEquals("APPROACHING", order.getDeliveryStage());

        ArgumentCaptor<DroneLockerCommLog> logs = ArgumentCaptor.forClass(DroneLockerCommLog.class);
        verify(commLogRepository, org.mockito.Mockito.atLeastOnce()).save(logs.capture());
        assertTrue(logs.getAllValues().stream().allMatch(log -> log.getOrderId() == null),
                "nhật ký thử nắp không được gắn đơn (Đơn #0)");
        assertTrue(logs.getAllValues().stream().anyMatch(log -> "LID_TEST_HOLD".equals(log.getEventType())));
    }

    /// Chế độ đang tắt, hoặc mã không thuộc danh sách ⇒ không mở nắp.
    @Test
    void lidTestDoesNothingWhenDisabledOrForAnUnlistedMarker() throws Exception {
        MqttClient mqtt = connectedMqtt();

        deliverSigned(lidTestMarker(7));
        assertTrue(sessions.isEmpty(), "chế độ tắt mà vẫn mở nắp");

        enableLidTest();
        deliverSigned(event("MARKER_DETECTED", Map.of("arucoMarkerId", 4, "arucoDictionary", "DICT_4X4_50",
                "mode", "LID_TEST"), OffsetDateTime.now()));
        assertTrue(sessions.isEmpty(), "mã ngoài danh sách mà vẫn mở nắp");
        verify(mqtt, never()).publish(eq("cabinet/" + LOCKER + "/command/open"), any(MqttMessage.class));
    }

    /// Pi hỏi mục tiêu: chỉ trả mã ArUco của ô đích khi nhiệm vụ ở chặng được phép mở nắp.
    @Test
    void targetRequestReturnsTheDestinationMarkerOnlyInAllowedStages() throws Exception {
        MqttClient mqtt = connectedMqtt();
        lenient().when(orderRepository.findById(21L)).thenAnswer(call -> Optional.of(order));
        ArgumentCaptor<MqttMessage> sent = ArgumentCaptor.forClass(MqttMessage.class);

        deliverSigned(event("TARGET_REQUEST", Map.of(), OffsetDateTime.now()));
        mission.setStatus("DEPARTED");
        deliverSigned(event("TARGET_REQUEST", Map.of(), OffsetDateTime.now()));

        verify(mqtt, times(2)).publish(eq(DroneLockerCommunicationService.DRONE_COMMANDS_TOPIC), sent.capture());
        var target = new ObjectMapper().readTree(new String(sent.getAllValues().get(0).getPayload()));
        assertEquals("DELIVERY_TARGET", target.get("eventType").asText());
        assertEquals(7, target.get("arucoMarkerId").asInt());
        assertEquals(LOCKER, target.get("lockerId").asLong());
        assertEquals(BOX, target.get("compartmentId").asLong());
        assertEquals(301L, target.get("missionId").asLong());
        var none = new ObjectMapper().readTree(new String(sent.getAllValues().get(1).getPayload()));
        assertEquals("NO_TARGET", none.get("eventType").asText());

        // Nhật ký chỉ ghi lần hỏi có mục tiêu — lần NO_TARGET (Pi hỏi theo nhịp) không làm đầy trang nhật ký.
        ArgumentCaptor<DroneLockerCommLog> logs = ArgumentCaptor.forClass(DroneLockerCommLog.class);
        verify(commLogRepository, org.mockito.Mockito.atLeastOnce()).save(logs.capture());
        var targetLogs = logs.getAllValues().stream()
                .filter(log -> "TARGET_REQUEST".equals(log.getEventType())).toList();
        assertEquals(1, targetLogs.size());
        assertEquals(null, targetLogs.getFirst().getErrorCode());
    }

    /// Tủ đã từng trả ACCEPTED (chạy code nắp drone mới) ⇒ backend được gửi lại nhanh khi mất ACK.
    @SuppressWarnings("unchecked")
    private void markLockerAckCapable() {
        ((java.util.Set<Long>) ReflectionTestUtils.getField(service, "ackCapableLockers")).add(LOCKER);
    }

    /// Tủ chưa từng gửi ACK (có thể là code cũ, chạy motor lại mỗi lần nhận lệnh) ⇒ không gửi lại theo hạn ACK.
    @Test
    void lockerNeverSeenAcknowledgingIsNotRetriedBeforeAFullLidRun() throws Exception {
        MqttClient mqtt = connectedMqtt();
        settings.update(Map.of("app.drone.delivery.lid-ack-timeout-seconds", 0), null);
        heartbeat(LOCKER);
        deliverSigned(marker(null, null));

        service.retryStalledLidCommands();
        service.retryStalledLidCommands();

        verify(mqtt, times(1)).publish(eq("cabinet/" + LOCKER + "/command/open"), any(MqttMessage.class));
    }

    @SuppressWarnings("unchecked")
    private void lockerLastSeen(long lockerId, LocalDateTime at) {
        ((Map<Long, LocalDateTime>) ReflectionTestUtils.getField(service, "lockerSeenAt")).put(lockerId, at);
    }

    private void forgetLidAttempts() {
        ((Map<?, ?>) ReflectionTestUtils.getField(service, "lidCommandAttempts")).clear();
    }

    private void lockerReply(long lockerId, String action, String commandId, String status, String hwState,
            String extraJson) {
        String body = "{\"commandId\":\"" + commandId + "\",\"boxId\":" + BOX + ",\"status\":\"" + status
                + "\",\"hwState\":\"" + hwState + "\"" + extraJson + "}";
        service.handleIncomingMessage("cabinet/" + lockerId + "/command/" + action + "/result", body, null);
    }

    // ------------------------------------------------------------------ helpers

    private String marker(Long orderId, Long lockerId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("arucoMarkerId", 7);
        fields.put("arucoDictionary", "DICT_4X4_50");
        fields.put("mode", "REAL_OUTDOOR");
        if (orderId != null) fields.put("orderId", orderId);
        if (lockerId != null) fields.put("lockerId", lockerId);
        return event("MARKER_DETECTED", fields, OffsetDateTime.now());
    }

    private String event(String type, Map<String, Object> extra, OffsetDateTime at) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messageId", UUID.randomUUID().toString());
        body.put("eventType", type);
        body.put("droneId", DRONE);
        body.put("timestamp", at.toString());
        body.putAll(extra);
        try {
            return new ObjectMapper().writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /// Lệnh nắp của drone gắn `lid: true` để Pi trượt nắp nóc tủ, không kích relay cửa trước của ô.
    @Test
    void lidCommandPayloadAsksCabinetToSlideTheLid() {
        var open = service.lidCommandPayload(BOX, "open", "cmd-open");
        assertTrue(open.get("lid").asBoolean());
        assertEquals("OPEN", open.get("action").asText());
        assertEquals(BOX, open.get("boxId").asLong());
        assertEquals("cmd-open", open.get("commandId").asText());

        assertTrue(service.lidCommandPayload(BOX, "CLOSE", "cmd-close").get("lid").asBoolean());
    }

    private void deliverSigned(String body) {
        String signature = DroneTelemetrySigner.sign(DroneTelemetrySigner.deviceKey(SECRET, DRONE),
                DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, body);
        service.handleIncomingMessage(DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, body, signature);
    }

    /// Kết quả của Pi tủ chạy code nắp drone: lệnh mở kèm xác nhận phần cứng (ở đây: chạy hết số bước).
    private void lockerResult(long lockerId, String action, String commandId, String hwState) {
        String body = "{\"commandId\":\"" + commandId + "\",\"boxId\":" + BOX
                + ",\"status\":\"SUCCESS\",\"hwState\":\"" + hwState + "\""
                + ("open".equals(action) ? ",\"lidConfirmation\":\"STEP_COUNT\"" : "") + "}";
        service.handleIncomingMessage("cabinet/" + lockerId + "/command/" + action + "/result", body, null);
    }
}
