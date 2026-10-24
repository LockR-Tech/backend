package com.huynqb.laundrylocker.order.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.BoxCellDto;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.model.DroneLockerCommLog;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneLockerCommLogRepository;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.service.DroneDeliveryMailer;
import com.huynqb.laundrylocker.order.service.DroneLockerCommunicationService;
import com.huynqb.laundrylocker.order.service.DroneMissionProgressService;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QA audit (24/10/2026) — mỗi test khẳng định HÀNH VI MONG ĐỢI của nghiệp vụ. Test FAIL nghĩa là
 * lỗi đã được tái hiện trên code hiện tại. Chỉ chạy khi bật -DqaAudit=true để không làm đỏ CI.
 * Code production thật (DroneMissionProgressService, DroneLockerCommunicationService, OrderRules);
 * chỉ thay database, service khác và broker MQTT bằng mock.
 */
@Tag("qa-audit")
@EnabledIfSystemProperty(named = "qaAudit", matches = "true")
class DroneFlowQaAuditTest {

    private final DroneMissionRepository missionRepository = mock(DroneMissionRepository.class);
    private final LockerOrderRepository orderRepository = mock(LockerOrderRepository.class);
    private final OrderStatusHistoryRepository historyRepository = mock(OrderStatusHistoryRepository.class);
    private final NotificationClient notificationClient = mock(NotificationClient.class);
    private final LockerDroneClient lockerDroneClient = mock(LockerDroneClient.class);
    private final LockerClient lockerClient = mock(LockerClient.class);
    private final UserClient userClient = mock(UserClient.class);
    private final DroneDeliveryMailer mailer = mock(DroneDeliveryMailer.class);
    private final DroneLockerCommLogRepository commLogRepository = mock(DroneLockerCommLogRepository.class);
    private final OrderRules rules = TestOrderRules.defaults();

    private DroneMissionProgressService progress;
    private DroneLockerCommunicationService comm;

    @BeforeEach
    void setUp() throws Exception {
        progress = new DroneMissionProgressService(missionRepository, orderRepository, historyRepository,
                notificationClient, lockerDroneClient, lockerClient, userClient, rules, mailer);
        Constructor<DroneLockerCommunicationService> ctor = DroneLockerCommunicationService.class
                .getDeclaredConstructor(DroneLockerCommLogRepository.class, LockerOrderRepository.class,
                        DroneMissionRepository.class, DroneMissionProgressService.class, LockerDroneClient.class,
                        OrderRules.class, ObjectMapper.class,
                        com.huynqb.laundrylocker.order.repository.DroneDeliverySessionRepository.class,
                        org.springframework.transaction.PlatformTransactionManager.class);
        comm = ctor.newInstance(commLogRepository, orderRepository, missionRepository, progress, lockerDroneClient,
                rules, new ObjectMapper(),
                mock(com.huynqb.laundrylocker.order.repository.DroneDeliverySessionRepository.class),
                mock(org.springframework.transaction.PlatformTransactionManager.class));
        when(commLogRepository.save(any(DroneLockerCommLog.class))).thenAnswer(call -> call.getArgument(0));
        when(notificationClient.notifyGuest(any())).thenReturn(ApiResponse.ok(
                new GuestNotification.Result(false, true, false, true)));
    }

    // ------------------------------------------------------------------ QA-01

    @Test
    @DisplayName("QA-01 Đơn đã huỷ không được bị đẩy sang READY_FOR_PICKUP và cấp PIN mới")
    void canceledOrderMustNotBecomeReadyForPickup() {
        LockerOrder order = order("CANCELED", "CANCELED");
        order.setPaymentStatus("REFUND_PENDING");
        DroneMission mission = mission("CANCELED");

        try {
            progress.advanceToStage(order, mission, "READY_FOR_PICKUP", null, "test");
        } catch (RuntimeException expectedRejection) {
            // Từ chối là đúng.
        }

        assertEquals("CANCELED", order.getStatus(), "đơn đã huỷ bị mở lại thành " + order.getStatus());
        assertNull(order.getPinCode(), "đơn đã huỷ được cấp PIN mở ô " + order.getPinCode());
    }

    // ------------------------------------------------------------------ QA-02

    @Test
    @DisplayName("QA-02 Bản tin MQTT không ký (MARKER_DETECTED + close result) không được mở lại đơn đã huỷ")
    void unsignedMqttEventsMustNotReopenACanceledOrder() {
        LockerOrder order = order("CANCELED", "CANCELED");
        DroneMission mission = mission("CANCELED");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        when(lockerDroneClient.getBoxCell(9001L)).thenReturn(ApiResponse.ok(
                new BoxCellDto(9001L, 1, "M", "DRONE", 0, 1, "RESERVED", null, false, "CLOSED", 5L, "DICT_4X4_50", 7, null)));

        // Bất kỳ ai publish được lên broker: không có chữ ký, không kiểm trạng thái đơn.
        comm.handleIncomingMessage(DroneLockerCommunicationService.DRONE_EVENTS_TOPIC,
                "{\"eventType\":\"MARKER_DETECTED\",\"orderId\":21,\"lockerId\":5,"
                        + "\"arucoMarkerId\":7,\"arucoDictionary\":\"DICT_4X4_50\",\"mode\":\"REAL_OUTDOOR\"}");
        comm.handleIncomingMessage("cabinet/5/command/close/result",
                "{\"commandId\":\"forged\",\"boxId\":9001,\"status\":\"SUCCESS\",\"hwState\":\"CLOSED\"}");

        assertEquals("CANCELED", order.getStatus(), "bản tin giả chuyển đơn đã huỷ thành " + order.getStatus()
                + " / chặng " + order.getDeliveryStage());
        assertNull(order.getPinCode(), "bản tin giả khiến hệ thống cấp và gửi PIN " + order.getPinCode());
    }

    // ------------------------------------------------------------------ QA-03

    @Test
    @DisplayName("QA-03 MARKER_DETECTED thiếu orderId/lockerId không được gán bừa cho đơn đang bay bất kỳ")
    void markerWithoutOrderOrLockerMustNotBindToAnArbitraryFlight() throws Exception {
        LockerOrder otherOrder = order("AWAITING_DISPATCH", "APPROACHING");
        otherOrder.setId(88L);
        otherOrder.setLockerId(99L);
        DroneMission otherMission = mission("APPROACHING");
        otherMission.setOrderId(88L);
        when(missionRepository.findByStatusIn(any())).thenReturn(List.of(otherMission));
        when(orderRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(otherOrder));
        when(missionRepository.findByOrderId(88L)).thenReturn(Optional.of(otherMission));
        when(lockerDroneClient.getBoxCell(9001L)).thenReturn(ApiResponse.ok(
                new BoxCellDto(9001L, 1, "M", "DRONE", 0, 1, "RESERVED", null, false, "CLOSED", 99L, "DICT_4X4_50", 7, null)));

        comm.handleIncomingMessage(DroneLockerCommunicationService.DRONE_EVENTS_TOPIC,
                "{\"eventType\":\"MARKER_DETECTED\",\"arucoMarkerId\":7,\"arucoDictionary\":\"DICT_4X4_50\"}");

        assertEquals("APPROACHING", otherOrder.getDeliveryStage(),
                "bản tin không định danh đơn/tủ đã đẩy đơn #88 sang " + otherOrder.getDeliveryStage());
    }

    // ------------------------------------------------------------------ QA-04

    @Test
    @DisplayName("QA-04 Locker-service không ghi nhận ô có hàng thì không được báo khách 'hàng đã vào ô' + gửi PIN")
    void depositMustNotSucceedWhenTheLockerDidNotConfirmTheBox() {
        LockerOrder order = order("AWAITING_DISPATCH", "ARRIVED");
        order.setFulfillmentMode("STANDARD");
        DroneMission mission = mission("ARRIVED");
        mission.setAssignedByUserId(99L);
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        doThrow(new RuntimeException("locker-service down")).when(lockerClient).occupyBox(anyLong());

        try {
            progress.advanceByOperator(21L, 99L);
        } catch (RuntimeException expectedRejection) {
            // Từ chối là đúng.
        }

        assertFalse("STORING".equals(order.getStatus()),
                "đơn đã STORING + PIN dù ô chưa được locker-service ghi nhận có hàng");
        verify(notificationClient, never()).notifyGuest(any());
    }

    // ------------------------------------------------------------------ QA-05

    @Test
    @DisplayName("QA-05 Cấu hình mặc định không được biến đơn khách thật thành DEMO (bay giả lập)")
    void defaultSettingsMustNotMakeRealCustomerOrdersDemo() throws Exception {
        // App mobile không gửi fulfillmentMode ⇒ server dùng resolveFulfillmentMode(null, userId).
        Method resolve = Class.forName("com.huynqb.laundrylocker.order.service.DroneOrderBookingService")
                .getDeclaredMethod("resolveFulfillmentMode", String.class, Long.class);
        resolve.setAccessible(true);
        Object booking = mockBookingServiceWith(rules);

        Object mode = resolve.invoke(booking, null, 12345L);

        assertEquals("STANDARD", mode, "đơn của khách thật (user 12345) mặc định là " + mode);
    }

    private static Object mockBookingServiceWith(OrderRules rules) throws Exception {
        Class<?> type = Class.forName("com.huynqb.laundrylocker.order.service.DroneOrderBookingService");
        Object instance = mock(type);
        var field = type.getDeclaredField("rules");
        field.setAccessible(true);
        field.set(instance, rules);
        return instance;
    }

    // ------------------------------------------------------------------ helpers

    private static LockerOrder order(String status, String stage) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(44L);
        order.setReceiverUserId(55L);
        order.setReceiverEmail("nhan@gmail.com");
        order.setType("DRONE_DELIVERY");
        order.setFulfillmentMode("STANDARD");
        order.setStatus(status);
        order.setDeliveryStage(stage);
        order.setLockerId(5L);
        order.setDestinationLockerId(5L);
        order.setReservedBoxId(9001L);
        order.setPaymentStatus("PAID");
        return order;
    }

    private static DroneMission mission(String status) {
        DroneMission mission = new DroneMission();
        mission.setId(301L);
        mission.setOrderId(21L);
        mission.setDroneUnitId(9L);
        mission.setDroneCode("DRONE-A1");
        mission.setStatus(status);
        return mission;
    }
}
