package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderComplaintRepository;
import com.huynqb.laundrylocker.order.repository.OrderDetailRepository;
import com.huynqb.laundrylocker.order.repository.OrderRatingRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.repository.PromotionClaimRepository;
import com.huynqb.laundrylocker.order.repository.PromotionRepository;
import com.huynqb.laundrylocker.order.repository.PromotionUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceBoxFaultTest {

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
                notificationClient,
                qrTokenService);
    }

    @Test
    void reportBoxFaultAutoCancelsInitializedUnpaidOrderAndKeepsBoxFaulted() {
        LockerOrder order = customerOrder();
        order.setId(22L);
        order.setStatus("INITIALIZED");
        order.setPaymentStatus("UNPAID");
        order.setSendBoxId(7003L);

        when(orderRepository.findById(22L)).thenReturn(Optional.of(order));
        when(lockerClient.reportFault(7003L, Map.of("reason", "Kẹt cửa"), 44L))
                .thenReturn(ApiResponse.ok(Map.of("boxId", 7003L, "status", "FAULT")));
        when(lockerClient.releaseBox(7003L))
                .thenReturn(ApiResponse.ok(new LockerBoxSummary(7L, 7003L, "CAB-07", 3, "FAULT")));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.reportBoxFault(22L, 44L, "Kẹt cửa");

        assertEquals("CANCELED", order.getStatus());
        InOrder calls = inOrder(lockerClient);
        calls.verify(lockerClient).reportFault(7003L, Map.of("reason", "Kẹt cửa"), 44L);
        calls.verify(lockerClient).releaseBox(7003L);
    }

    @Test
    void reportBoxFaultDoesNotAutoCancelPaidOrder() {
        LockerOrder order = customerOrder();
        order.setId(23L);
        order.setStatus("INITIALIZED");
        order.setPaymentStatus("PAID");
        order.setSendBoxId(7004L);

        when(orderRepository.findById(23L)).thenReturn(Optional.of(order));
        when(lockerClient.reportFault(7004L, Map.of("reason", "Kẹt cửa"), 44L))
                .thenReturn(ApiResponse.ok(Map.of("boxId", 7004L, "status", "FAULT")));

        orderService.reportBoxFault(23L, 44L, "Kẹt cửa");

        assertEquals("INITIALIZED", order.getStatus());
        verify(lockerClient, never()).releaseBox(any());
    }

    @Test
    void reportBoxFaultRejectsCompletedOrder() {
        LockerOrder order = customerOrder();
        order.setId(26L);
        order.setStatus("COMPLETED");
        order.setPaymentStatus("PAID");
        order.setSendBoxId(7006L);

        when(orderRepository.findById(26L)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> orderService.reportBoxFault(26L, 44L, "Kẹt cửa"));

        assertEquals("ORDER_STATUS_INVALID", error.getCode());
        verify(lockerClient, never()).reportFault(any(), any(), any());
    }

    @Test
    void reportBoxFaultAllowsMissingReason() {
        LockerOrder order = customerOrder();
        order.setId(27L);
        order.setStatus("INITIALIZED");
        order.setPaymentStatus("UNPAID");
        order.setSendBoxId(7007L);

        when(orderRepository.findById(27L)).thenReturn(Optional.of(order));
        when(lockerClient.reportFault(7007L, Map.of(), 44L))
                .thenReturn(ApiResponse.ok(Map.of("boxId", 7007L, "status", "FAULT")));
        when(lockerClient.releaseBox(7007L))
                .thenReturn(ApiResponse.ok(new LockerBoxSummary(7L, 7007L, "CAB-07", 7, "FAULT")));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.reportBoxFault(27L, 44L, null);

        verify(lockerClient).reportFault(7007L, Map.of(), 44L);
        assertEquals("CANCELED", order.getStatus());
    }

    @Test
    void reportBoxFaultRejectsNonOwner() {
        LockerOrder order = customerOrder();
        order.setId(24L);
        order.setSendBoxId(7005L);
        when(orderRepository.findById(24L)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> orderService.reportBoxFault(24L, 77L, "Kẹt cửa"));

        assertEquals("ORDER_FORBIDDEN", error.getCode());
        verify(lockerClient, never()).reportFault(any(), any(), any());
    }

    @Test
    void cancelRequiresAuthenticatedOwner() {
        LockerOrder order = customerOrder();
        order.setId(25L);
        order.setStatus("INITIALIZED");
        when(orderRepository.findById(25L)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> orderService.cancel(25L, null, null));

        assertEquals("ORDER_FORBIDDEN", error.getCode());
    }

    private LockerOrder customerOrder() {
        LockerOrder order = new LockerOrder();
        order.setUserId(44L);
        order.setLockerId(7L);
        order.setType("RENTAL");
        order.setServiceCategory("RENTAL");
        order.setTotalPrice(BigDecimal.valueOf(15000));
        order.setOriginalPrice(BigDecimal.valueOf(15000));
        return order;
    }
}
