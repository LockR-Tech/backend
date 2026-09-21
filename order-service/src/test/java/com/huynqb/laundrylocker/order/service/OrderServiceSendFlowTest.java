package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.*;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceSendFlowTest {

    @Mock private LockerOrderRepository orderRepository;
    @Mock private OrderDetailRepository detailRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private OrderRatingRepository ratingRepository;
    @Mock private OrderComplaintRepository complaintRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private PromotionUsageRepository promotionUsageRepository;
    @Mock private PromotionClaimRepository promotionClaimRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private UserClient userClient;
    @Mock private LockerClient lockerClient;
    @Mock private LockerCellClient lockerCellClient;
    @Mock private LockerLookupClient lockerLookupClient;
    @Mock private NotificationClient notificationClient;
    @Mock private QrTokenService qrTokenService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
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
                TestOrderRules.of(Map.of(
                        "app.order.require-payment-before-drop", true,
                        "app.order.send-confirm-requires-open", true,
                        "app.order.receiver-notify-sms", false,
                        "app.order.receiver-notify-email", false)));
    }

    @Test
    void confirmSendRejectedWhenBoxWasNeverOpened() {
        LockerOrder order = sendAwaitingDrop(31L, "PAID");
        when(orderRepository.findById(31L)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(BusinessException.class, () -> orderService.confirm(31L, 44L));

        assertEquals("ORDER_DROP_NOT_OPENED", error.getCode());
        assertEquals("INITIALIZED", order.getStatus());
        verify(lockerClient, never()).occupyBox(any());
    }

    @Test
    void confirmSendRejectsUnpaidOrder() {
        LockerOrder order = sendAwaitingDrop(32L, "UNPAID");
        order.setDropOpenedAt(LocalDateTime.now().minusMinutes(1));
        when(orderRepository.findById(32L)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(BusinessException.class, () -> orderService.confirm(32L, 44L));

        assertEquals("ORDER_UNPAID", error.getCode());
        verify(lockerClient, never()).occupyBox(any());
    }

    @Test
    void confirmSendAfterOpeningSwapsDropPinForPickupPin() {
        LockerOrder order = sendAwaitingDrop(33L, "PAID");
        order.setDropOpenedAt(LocalDateTime.now().minusMinutes(1));
        when(orderRepository.findById(33L)).thenReturn(Optional.of(order));
        when(lockerClient.occupyBox(901L))
                .thenReturn(ApiResponse.ok(new LockerBoxSummary(5L, 901L, "CAB-05", 4, "OCCUPIED")));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = orderService.confirm(33L, 44L);

        assertEquals("STORING", response.status());
        assertNotEquals("111111", order.getPinCode());
        assertNotNull(order.getPickupDeadline());
        verify(lockerClient).occupyBox(901L);
    }

    @Test
    void markDropOpenedRecordsFirstOpenOnly() {
        LockerOrder order = sendAwaitingDrop(34L, "PAID");
        when(orderRepository.findById(34L)).thenReturn(Optional.of(order));

        orderService.markDropOpened(34L);
        LocalDateTime first = order.getDropOpenedAt();
        orderService.markDropOpened(34L);

        assertNotNull(first);
        assertEquals(first, order.getDropOpenedAt());
        verify(orderRepository).save(order);
    }

    @Test
    void markDropOpenedIgnoresOrdersPastDropOff() {
        LockerOrder order = sendAwaitingDrop(35L, "PAID");
        order.setStatus("STORING");
        when(orderRepository.findById(35L)).thenReturn(Optional.of(order));

        orderService.markDropOpened(35L);

        assertEquals(null, order.getDropOpenedAt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void statusReportsPaidFromPaymentStatusNotOrderCompletion() {
        LockerOrder paid = sendAwaitingDrop(36L, "PAID");
        LockerOrder unpaid = sendAwaitingDrop(37L, "UNPAID");
        when(orderRepository.findById(36L)).thenReturn(Optional.of(paid));
        when(orderRepository.findById(37L)).thenReturn(Optional.of(unpaid));

        assertTrue(orderService.status(36L).isPaid());
        assertFalse(orderService.status(37L).isPaid());
        assertEquals("UNPAID", orderService.status(37L).paymentStatus());
    }

    private static LockerOrder sendAwaitingDrop(Long id, String paymentStatus) {
        LockerOrder order = new LockerOrder();
        order.setId(id);
        order.setUserId(44L);
        order.setOrderCode("ORD-" + id);
        order.setType("SEND");
        order.setServiceCategory("SEND");
        order.setStatus("INITIALIZED");
        order.setSendBoxId(901L);
        order.setLockerId(5L);
        order.setPinCode("111111");
        order.setReceiverPhone("0909000000");
        order.setTotalPrice(BigDecimal.valueOf(15000));
        order.setOriginalPrice(BigDecimal.valueOf(15000));
        order.setPaymentStatus(paymentStatus);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        return order;
    }
}
