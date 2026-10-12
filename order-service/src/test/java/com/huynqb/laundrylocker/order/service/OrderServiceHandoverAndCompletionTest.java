package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.DelegateOrderRequest;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.*;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Bàn giao trực tiếp phải xác minh người nhận; đơn hoàn tất phát `order.completed` đúng một lần;
/// ủy quyền lưu riêng thông tin người lấy hộ.
@ExtendWith(MockitoExtension.class)
class OrderServiceHandoverAndCompletionTest {

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
                TestOrderRules.defaults());
        lenient().when(orderRepository.save(any(LockerOrder.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(detailRepository.findByOrderId(any())).thenReturn(List.of());
    }

    @Test
    void handoverWithoutCodeIsRefused() {
        when(orderRepository.findById(21L)).thenReturn(Optional.of(storedOrder()));

        BusinessException error = assertThrows(BusinessException.class, () -> orderService.directHandover(21L, null));

        assertEquals("HANDOVER_OTP_REQUIRED", error.getCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void handoverWithWrongCodeIsRefused() {
        when(orderRepository.findById(21L)).thenReturn(Optional.of(storedOrder()));

        BusinessException error = assertThrows(BusinessException.class, () -> orderService.directHandover(21L, "000000"));

        assertEquals("INVALID_OTP", error.getCode());
    }

    @Test
    void handoverOfOrderWithoutActiveCodeCannotBeVerified() {
        LockerOrder order = storedOrder();
        order.setPinCode(null);
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(BusinessException.class, () -> orderService.directHandover(21L, "123456"));

        assertEquals("HANDOVER_OTP_UNAVAILABLE", error.getCode());
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void handoverWithPickupPinCompletesAndPublishesCompletionOnce() {
        LockerOrder order = storedOrder();
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        orderService.directHandover(21L, " 123456 ");

        assertEquals("COMPLETED", order.getStatus());
        assertNotNull(order.getCompletedAt());
        ArgumentCaptor<DomainEvent> completed = ArgumentCaptor.forClass(DomainEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(DomainEventNames.EXCHANGE), eq(DomainEventNames.ORDER_COMPLETED), completed.capture());
        Map<String, Object> payload = completed.getValue().payload();
        assertEquals(21L, payload.get("orderId"));
        assertEquals(10L, payload.get("userId"));
        assertEquals(new BigDecimal("15000"), payload.get("paidAmount"));
        assertEquals("SEND", payload.get("type"));
        assertNotNull(payload.get("completedAt"));
    }

    @Test
    void handoverAcceptsTheOrderQrCode() {
        LockerOrder order = storedOrder();
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(qrTokenService.matches("LLQR.l.sig", 21L, "123456")).thenReturn(true);

        orderService.directHandover(21L, "LLQR.l.sig");

        assertEquals("COMPLETED", order.getStatus());
    }

    @Test
    void nonCompletingTransitionsDoNotPublishCompletion() {
        LockerOrder order = storedOrder();
        order.setStatus("INITIALIZED");
        order.setPaymentStatus("PAID");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(promotionUsageRepository.findByOrderId(21L)).thenReturn(List.of());

        orderService.cancel(21L, null, 10L);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), eq(DomainEventNames.ORDER_COMPLETED), any(Object.class));
        verify(rabbitTemplate).convertAndSend(anyString(), eq(DomainEventNames.ORDER_STATUS_CHANGED), any(Object.class));
    }

    @Test
    void delegationRecordsTheDelegateSeparately() {
        LockerOrder order = storedOrder();
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        orderService.delegate(21L, 10L, new DelegateOrderRequest(" 0907654321 ", "Người Lấy Hộ", "Lấy giúp"));

        assertEquals("0907654321", order.getDelegatePhone());
        assertEquals("Người Lấy Hộ", order.getDelegateName());
        assertEquals("Lấy giúp", order.getDelegateNote());
        assertNotNull(order.getDelegatedAt());
    }

    private static LockerOrder storedOrder() {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(10L);
        order.setType("SEND");
        order.setStatus("STORING");
        order.setPinCode("123456");
        order.setPaymentStatus("PAID");
        order.setTotalPrice(new BigDecimal("15000"));
        order.setPaidAmount(new BigDecimal("15000"));
        order.setSendBoxId(70L);
        return order;
    }
}
