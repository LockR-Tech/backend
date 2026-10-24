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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private final OrderRules rules = TestOrderRules.defaults();

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

    private DroneLockerCommunicationService indoorService(boolean enabled) {
        OrderRules demoRules = TestOrderRules.of(Map.of(
                com.huynqb.laundrylocker.order.settings.OrderSettingsCatalog.DRONE_DEMO_INDOOR_ARUCO_ENABLED, enabled));
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

    private void deliverSigned(String body) {
        String signature = DroneTelemetrySigner.sign(DroneTelemetrySigner.deviceKey(SECRET, DRONE),
                DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, body);
        service.handleIncomingMessage(DroneLockerCommunicationService.DRONE_EVENTS_TOPIC, body, signature);
    }

    private void lockerResult(long lockerId, String action, String commandId, String hwState) {
        String body = "{\"commandId\":\"" + commandId + "\",\"boxId\":" + BOX
                + ",\"status\":\"SUCCESS\",\"hwState\":\"" + hwState + "\"}";
        service.handleIncomingMessage("cabinet/" + lockerId + "/command/" + action + "/result", body, null);
    }
}
