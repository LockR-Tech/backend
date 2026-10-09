package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.settings.TestOrderRules;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.CellDto;
import com.huynqb.laundrylocker.order.dto.CreateDroneDeliveryOrderRequest;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryOrderResponse;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceDroneDeliveryTest {

    @Mock
    private LockerOrderRepository orderRepository;
    @Mock
    private OrderDetailRepository detailRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;
    @Mock
    private OrderRatingRepository ratingRepository;
    @Mock
    private OrderComplaintRepository complaintRepository;
    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private PromotionUsageRepository promotionUsageRepository;
    @Mock
    private PromotionClaimRepository promotionClaimRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private UserClient userClient;
    @Mock
    private LockerClient lockerClient;
    @Mock
    private LockerCellClient lockerCellClient;

    @Mock
    private LockerLookupClient lockerLookupClient;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private QrTokenService qrTokenService;

    private OrderService orderService;

    private com.huynqb.laundrylocker.common.settings.BusinessSettings settings;

    @BeforeEach
    void setUp() {
        settings = TestOrderRules.settings(java.util.Map.of("app.order.drone-delivery-fee", 15000));
        orderService =
                new OrderService(
                        orderRepository,
                        detailRepository,
                        historyRepository,
                        ratingRepository,
                        complaintRepository,
                        promotionRepository,
                        promotionUsageRepository,
                        promotionClaimRepository,
                        rabbitTemplate,
                        userClient,
                        lockerClient,
                        lockerCellClient,
                        lockerLookupClient,
                        notificationClient,
                        qrTokenService,
                        new com.huynqb.laundrylocker.order.settings.OrderRules(settings));
    }

    @Test
    void createDroneDeliveryReturnsExistingOrderForSameIdempotencyKey() {
        LockerOrder existing = droneOrder(11L, 44L, 5L, 9001L, "idem-1");
        when(orderRepository.findByUserIdAndIdempotencyKey(44L, "idem-1")).thenReturn(Optional.of(existing));

        DroneDeliveryOrderResponse response =
                orderService.createDroneDelivery(
                        new CreateDroneDeliveryOrderRequest(3L, 5L, 9001L, "Tai lieu", 1200, "CASH"), 44L, "idem-1");

        assertEquals(existing.getId(), response.orderId());
        assertEquals(existing.getReservedBoxId(), response.reservedBoxId());
        verify(lockerClient, never()).reserveBox(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createDroneDeliveryReservesDroneBoxAndPersistsOrder() {
        when(orderRepository.findByUserIdAndIdempotencyKey(44L, "idem-2")).thenReturn(Optional.empty());
        when(userClient.getUser(44L))
                .thenReturn(ApiResponse.ok(new UserSummary(44L, "u@test", "0901", "User", "ACTIVE")));
        when(userClient.getUsersByRole("DRONE_TECHNICIAN"))
                .thenReturn(
                        ApiResponse.ok(
                                List.of(
                                        new UserSummary(
                                                91L, "m1@test", "0911", "M1", "ACTIVE", Set.of("DRONE_TECHNICIAN")),
                                        new UserSummary(
                                                92L, "m2@test", "0912", "M2", "ACTIVE", Set.of("DRONE_TECHNICIAN")))));
        when(lockerLookupClient.getLockers(any()))
                .thenReturn(ApiResponse.ok(List.of(droneLocker(3L), droneLocker(5L))));
        // Người gửi không chọn ô ⇒ hệ thống lấy một ô DRONE trống của tủ gửi.
        when(lockerCellClient.findAvailable(3L, null, "DRONE"))
                .thenReturn(ApiResponse.ok(new CellDto(8001L, 1, "M", "DRONE", 0, 0, "AVAILABLE", null)));
        when(lockerClient.reserveBox(8001L, "DRONE"))
                .thenReturn(ApiResponse.ok(new LockerBoxSummary(3L, 8001L, "CAB-03", 1, "RESERVED")));
        when(lockerCellClient.getCell(9001L))
                .thenReturn(ApiResponse.ok(new CellDto(9001L, 7, "M", "DRONE", 0, 0, "AVAILABLE", null)));
        when(lockerClient.reserveBox(9001L, "DRONE"))
                .thenReturn(ApiResponse.ok(new LockerBoxSummary(5L, 9001L, "CAB-05", 7, "RESERVED")));
        when(orderRepository.save(any(LockerOrder.class)))
                .thenAnswer(invocation -> {
                    LockerOrder saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(77L);
                    }
                    return saved;
                });

        DroneDeliveryOrderResponse response =
                orderService.createDroneDelivery(
                        new CreateDroneDeliveryOrderRequest(3L, 5L, 9001L, "Tai lieu", 1200, "CASH"), 44L, "idem-2");

        assertEquals(77L, response.orderId());
        assertEquals(9001L, response.reservedBoxId());
        assertEquals("DRONE_DELIVERY", response.type());
        assertEquals("AWAITING_DISPATCH", response.deliveryStage());
        assertEquals("DEMO", response.fulfillmentMode());
        assertEquals(3L, response.sourceLockerId());
        assertEquals(5L, response.destinationLockerId());
        verify(lockerClient).reserveBox(9001L, "DRONE");
        // Ô gửi ở Locker A cũng được giữ để người khác không đặt chồng lên.
        verify(lockerClient).reserveBox(8001L, "DRONE");
        verify(notificationClient, org.mockito.Mockito.times(3)).requestNotification(any());
    }

    @Test
    void createDroneDeliveryReleasesSourceCellWhenDestinationCellCannotBeHeld() {
        when(orderRepository.findByUserIdAndIdempotencyKey(44L, "idem-5")).thenReturn(Optional.empty());
        when(userClient.getUser(44L))
                .thenReturn(ApiResponse.ok(new UserSummary(44L, "u@test", "0901", "User", "ACTIVE")));
        when(lockerLookupClient.getLockers(any()))
                .thenReturn(ApiResponse.ok(List.of(droneLocker(3L), droneLocker(5L))));
        when(lockerCellClient.getCell(8001L))
                .thenReturn(ApiResponse.ok(new CellDto(8001L, 1, "M", "DRONE", 0, 0, "AVAILABLE", null)));
        when(lockerClient.getBox(8001L))
                .thenReturn(ApiResponse.ok(new LockerBoxSummary(3L, 8001L, "CAB-03", 1, "AVAILABLE")));
        when(lockerClient.reserveBox(8001L, "DRONE"))
                .thenReturn(ApiResponse.ok(new LockerBoxSummary(3L, 8001L, "CAB-03", 1, "RESERVED")));
        // Tủ nhận hết ô DRONE trống.
        when(lockerCellClient.findAvailable(5L, null, "DRONE")).thenReturn(ApiResponse.ok(null));

        BusinessException error =
                assertThrows(
                        BusinessException.class,
                        () ->
                                orderService.createDroneDelivery(
                                        new CreateDroneDeliveryOrderRequest(
                                                3L, 5L, null, "Tai lieu", 1200, "WALLET", null, 8001L),
                                        44L,
                                        "idem-5"));

        assertEquals("BOX_NOT_AVAILABLE", error.getCode());
        verify(lockerClient).releaseBox(8001L);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createDroneDeliveryRejectsSourceCellOfAnotherLocker() {
        when(orderRepository.findByUserIdAndIdempotencyKey(44L, "idem-6")).thenReturn(Optional.empty());
        when(userClient.getUser(44L))
                .thenReturn(ApiResponse.ok(new UserSummary(44L, "u@test", "0901", "User", "ACTIVE")));
        when(lockerLookupClient.getLockers(any()))
                .thenReturn(ApiResponse.ok(List.of(droneLocker(3L), droneLocker(5L))));
        when(lockerCellClient.getCell(8001L))
                .thenReturn(ApiResponse.ok(new CellDto(8001L, 1, "M", "DRONE", 0, 0, "AVAILABLE", null)));
        when(lockerClient.getBox(8001L))
                .thenReturn(ApiResponse.ok(new LockerBoxSummary(9L, 8001L, "CAB-09", 1, "AVAILABLE")));

        BusinessException error =
                assertThrows(
                        BusinessException.class,
                        () ->
                                orderService.createDroneDelivery(
                                        new CreateDroneDeliveryOrderRequest(
                                                3L, 5L, null, "Tai lieu", 1200, "WALLET", null, 8001L),
                                        44L,
                                        "idem-6"));

        assertEquals("DRONE_SOURCE_CELL_MISMATCH", error.getCode());
        verify(lockerClient, never()).reserveBox(any(), any());
    }

    @Test
    void createDroneDeliveryRequiresTheProhibitedItemsDeclaration() {
        assertCreateRejected(parcel(null, null, null, null, null, null), "DRONE_PROHIBITED_ITEMS_NOT_DECLARED");
        assertCreateRejected(parcel(null, null, null, null, null, false), "DRONE_PROHIBITED_ITEMS_NOT_DECLARED");
    }

    @Test
    void createDroneDeliveryRejectsParcelsThatDoNotFitTheCargoBay() {
        // Khoang mặc định 30 x 25 x 20 cm; kiện xoay được nên 20 x 30 x 25 vẫn lọt.
        assertCreateRejected(parcel(40, 10, 10, null, null, true), "DRONE_PARCEL_TOO_LARGE");
        assertCreateRejected(parcel(26, 26, 10, null, null, true), "DRONE_PARCEL_TOO_LARGE");
        assertCreateRejected(parcel(20, null, 10, null, null, true), "DRONE_PARCEL_SIZE_INVALID");
        assertCreateRejected(parcel(null, null, null, "WEAPON", null, true), "DRONE_PARCEL_CATEGORY_INVALID");
        assertCreateRejected(
                parcel(null, null, null, null, new java.math.BigDecimal("2000001"), true),
                "DRONE_DECLARED_VALUE_TOO_HIGH");
    }

    @Test
    void createDroneDeliveryRejectsRoutesBeyondTheDroneRange() {
        var near = new com.huynqb.laundrylocker.order.dto.admin.LockerInfo(
                3L, 1L, "LK-3", "Locker 3", "ACTIVE", "A", 10.70, 106.70, true, null, 8, 4);
        // Cách ~11 km về phía bắc, vượt tầm mặc định 5 km.
        var far = new com.huynqb.laundrylocker.order.dto.admin.LockerInfo(
                5L, 1L, "LK-5", "Locker 5", "ACTIVE", "B", 10.80, 106.70, true, null, 8, 4);
        when(lockerLookupClient.getLockers(any())).thenReturn(ApiResponse.ok(List.of(near, far)));

        assertCreateRejected(parcel(20, 30, 25, "food", null, true), "DRONE_ROUTE_TOO_FAR");
    }

    @Test
    void createDroneDeliveryIsBlockedWhileFlightsAreSuspendedOrTheCustomerHasTooManyOpenOrders() {
        when(orderRepository.countByUserIdAndTypeAndStatus(44L, "DRONE_DELIVERY", "AWAITING_DISPATCH"))
                .thenReturn(3L);
        assertCreateRejected(parcel(null, null, null, null, null, true), "DRONE_OPEN_ORDER_LIMIT");

        settings.update(java.util.Map.of("app.order.drone-flights-suspended", true), null);
        assertCreateRejected(parcel(null, null, null, null, null, true), "DRONE_FLIGHTS_SUSPENDED");
    }

    private void assertCreateRejected(CreateDroneDeliveryOrderRequest request, String expectedCode) {
        BusinessException error = assertThrows(
                BusinessException.class, () -> orderService.createDroneDelivery(request, 44L, "idem-rule"));

        assertEquals(expectedCode, error.getCode());
        verify(lockerClient, never()).reserveBox(any(), any());
        verify(orderRepository, never()).save(any());
    }

    private static CreateDroneDeliveryOrderRequest parcel(
            Integer length,
            Integer width,
            Integer height,
            String category,
            java.math.BigDecimal declaredValue,
            Boolean prohibitedItemsDeclared) {
        return new CreateDroneDeliveryOrderRequest(
                3L, 5L, null, "Tai lieu", 1200, null, null, 8001L, null, null, null,
                length, width, height, category, declaredValue, false, prohibitedItemsDeclared);
    }

    @Test
    void explicitDemoIsRejectedForUserOutsideConfiguredAllowlistBeforeBoxReservation() {
        settings.update(java.util.Map.of("app.drone.demo.allowed-user-ids", "91,92"), null);

        BusinessException error =
                assertThrows(
                        BusinessException.class,
                        () ->
                                orderService.createDroneDelivery(
                                        new CreateDroneDeliveryOrderRequest(
                                                3L, 5L, 9001L, "Tai lieu", 1200, "CASH", "DEMO"),
                                        44L,
                                        "idem-denied"));

        assertEquals("DRONE_DEMO_NOT_ALLOWED", error.getCode());
        verify(lockerClient, never()).reserveBox(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void getByAccessBlocksUnpaidDronePickupWhenReadyForPickup() {
        LockerOrder order = droneOrder(33L, 44L, 5L, 9001L, "idem-4");
        order.setStatus("STORING");
        order.setDeliveryStage("READY_FOR_PICKUP");
        order.setPinCode("123456");
        when(orderRepository.findByPinCode("123456")).thenReturn(Optional.of(order));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> orderService.getByAccess("123456"));

        assertEquals("DRONE_PAYMENT_REQUIRED_BEFORE_PICKUP", ex.getCode());
    }

    @Test
    void completingPickupEndsTheDeliveryStageToo() {
        LockerOrder order = droneOrder(41L, 44L, 5L, 9001L, "idem-7");
        order.setStatus("STORING");
        order.setDeliveryStage("READY_FOR_PICKUP");
        order.setPaymentStatus("PAID");
        when(orderRepository.findById(41L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = orderService.complete(41L, 44L);

        // Trước đây deliveryStage giữ READY_FOR_PICKUP nên app vẫn hiện "Chờ nhận hàng".
        assertEquals("COMPLETED", order.getStatus());
        assertEquals("COMPLETED", order.getDeliveryStage());
        assertEquals("DONE", response.nextAction());
        verify(lockerClient).releaseBox(9001L);
    }

    @Test
    void cancelDroneDeliveryReleasesReservedBox() {
        LockerOrder order = droneOrder(21L, 44L, 5L, 9001L, "idem-3");
        order.setSourceBoxId(8001L);
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DroneDeliveryOrderResponse response = orderService.getDroneDelivery(21L, 44L);
        assertSame(order.getId(), response.orderId());

        orderService.cancel(21L, null, 44L);

        verify(lockerClient).releaseBox(9001L);
        verify(lockerClient).releaseBox(8001L);
        assertEquals("CANCELED", order.getStatus());
        assertEquals("CANCELED", order.getDeliveryStage());
    }

    @Test
    void customerCancelAfterDropOffKeepsTheSourceCellHoldingTheParcel() {
        LockerOrder order = droneOrder(21L, 44L, 5L, 9001L, "idem-8");
        order.setSourceBoxId(8001L);
        order.setPaymentStatus("PAID");
        order.setParcelDroppedAt(java.time.LocalDateTime.now());
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.cancel(21L, null, 44L);

        assertEquals("CANCELED", order.getStatus());
        verify(lockerClient).releaseBox(9001L);
        // Kiện còn trong ô gửi: ô chỉ được nhả khi đội bay xác nhận đã trả kiện.
        verify(lockerClient, never()).releaseBox(8001L);
        assertEquals(8001L, order.getSourceBoxId());
    }

    private com.huynqb.laundrylocker.order.dto.admin.LockerInfo droneLocker(Long id) {
        return new com.huynqb.laundrylocker.order.dto.admin.LockerInfo(
                id, 1L, "LK-" + id, "Locker " + id, "ACTIVE", "Dia chi " + id, 10.7, 106.7, true, null, 8, 4);
    }

    private LockerOrder droneOrder(Long id, Long userId, Long lockerId, Long boxId, String idempotencyKey) {
        LockerOrder order = new LockerOrder();
        order.setId(id);
        order.setOrderCode("ORD-DRONE-" + id);
        order.setUserId(userId);
        order.setReceiverId(userId);
        order.setLockerId(lockerId);
        order.setDestinationLockerId(lockerId);
        order.setSendBoxId(boxId);
        order.setReservedBoxId(boxId);
        order.setType("DRONE_DELIVERY");
        order.setServiceCategory("DRONE_DELIVERY");
        order.setStatus("AWAITING_DISPATCH");
        order.setPaymentStatus("UNPAID");
        order.setDeliveryStage("AWAITING_DISPATCH");
        order.setParcelWeightGrams(1200);
        order.setIdempotencyKey(idempotencyKey);
        order.setTotalPrice(BigDecimal.valueOf(15000));
        order.setOriginalPrice(BigDecimal.valueOf(15000));
        return order;
    }
}
