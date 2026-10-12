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
