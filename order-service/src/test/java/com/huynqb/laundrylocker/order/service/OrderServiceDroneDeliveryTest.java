package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
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
    private NotificationClient notificationClient;
    @Mock
    private QrTokenService qrTokenService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
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
                        notificationClient,
                        qrTokenService);
        ReflectionTestUtils.setField(orderService, "sendBaseFee", 15000L);
        ReflectionTestUtils.setField(orderService, "droneDemoEnabled", true);
        ReflectionTestUtils.setField(orderService, "droneDemoAllowedUserIds", "");
    }

    @Test
    void createDroneDeliveryReturnsExistingOrderForSameIdempotencyKey() {
        LockerOrder existing = droneOrder(11L, 44L, 5L, 9001L, "idem-1");
        when(orderRepository.findByUserIdAndIdempotencyKey(44L, "idem-1")).thenReturn(Optional.of(existing));

        DroneDeliveryOrderResponse response =
                orderService.createDroneDelivery(
                        new CreateDroneDeliveryOrderRequest(5L, 9001L, "Tai lieu", 1200, "CASH"), 44L, "idem-1");

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
        when(userClient.getUsersByRole("MAINTENANCE"))
                .thenReturn(
                        ApiResponse.ok(
                                List.of(
                                        new UserSummary(91L, "m1@test", "0911", "M1", "ACTIVE", Set.of("MAINTENANCE")),
                                        new UserSummary(92L, "m2@test", "0912", "M2", "ACTIVE", Set.of("MAINTENANCE")))));
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
                        new CreateDroneDeliveryOrderRequest(5L, 9001L, "Tai lieu", 1200, "CASH"), 44L, "idem-2");

        assertEquals(77L, response.orderId());
        assertEquals(9001L, response.reservedBoxId());
        assertEquals("DRONE_DELIVERY", response.type());
        assertEquals("AWAITING_DISPATCH", response.deliveryStage());
        assertEquals("DEMO", response.fulfillmentMode());
        verify(lockerClient).reserveBox(9001L, "DRONE");
        verify(notificationClient, org.mockito.Mockito.times(2)).requestNotification(any());
    }

    @Test
    void explicitDemoIsRejectedForUserOutsideConfiguredAllowlistBeforeBoxReservation() {
        ReflectionTestUtils.setField(orderService, "droneDemoAllowedUserIds", "91,92");

        BusinessException error =
                assertThrows(
                        BusinessException.class,
                        () ->
                                orderService.createDroneDelivery(
                                        new CreateDroneDeliveryOrderRequest(
                                                5L, 9001L, "Tai lieu", 1200, "CASH", "DEMO"),
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
    void cancelDroneDeliveryReleasesReservedBox() {
        LockerOrder order = droneOrder(21L, 44L, 5L, 9001L, "idem-3");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DroneDeliveryOrderResponse response = orderService.getDroneDelivery(21L, 44L);
        assertSame(order.getId(), response.orderId());

        orderService.cancel(21L, null, 44L);

        verify(lockerClient).releaseBox(9001L);
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
