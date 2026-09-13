package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.RentalOrderRequest;
import com.huynqb.laundrylocker.order.dto.CellDto;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.*;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceRentalPaymentTest {

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
        ReflectionTestUtils.setField(orderService, "rentalRateStandard", 5000L);
        ReflectionTestUtils.setField(orderService, "requirePaymentBeforeDrop", true);
    }

    @Test
    void extendRentalMarksPreviouslyPaidRentalAsUnpaidUntilExtraChargeIsSettled() {
        LockerOrder order = new LockerOrder();
        order.setId(12L);
        order.setUserId(44L);
        order.setType("RENTAL");
        order.setStatus("STORING");
        order.setSendBoxId(901L);
        order.setPickupDeadline(LocalDateTime.now().plusHours(2));
        order.setTotalPrice(BigDecimal.valueOf(10000));
        order.setOriginalPrice(BigDecimal.valueOf(10000));
        order.setPaymentStatus("PAID");
        order.setPaidAt(LocalDateTime.now().minusHours(1));

        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));
        when(lockerCellClient.getCell(901L)).thenReturn(com.huynqb.laundrylocker.common.dto.ApiResponse.ok(
                new CellDto(901L, 1, "S", "STANDARD", 0, 0, "OCCUPIED", null)));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.extendRental(12L, 44L, 2);

        assertEquals(BigDecimal.valueOf(20000), order.getTotalPrice());
        assertEquals(BigDecimal.valueOf(20000), order.getOriginalPrice());
        assertEquals("UNPAID", order.getPaymentStatus());
        assertNull(order.getPaidAt());
    }

    @Test
    void pickupStorageRejectsRentalWhenExtensionBalanceIsStillUnpaid() {
        LockerOrder order = new LockerOrder();
        order.setId(13L);
        order.setUserId(44L);
        order.setType("RENTAL");
        order.setStatus("STORING");
        order.setSendBoxId(902L);
        order.setTotalPrice(BigDecimal.valueOf(15000));
        order.setPaymentStatus("UNPAID");

        when(orderRepository.findById(13L)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> orderService.pickupStorage(13L, 44L));

        assertEquals("ORDER_UNPAID", error.getCode());
        verify(lockerClient, never()).releaseBox(any());
    }

    @Test
    void createRentalKeepsDeadlineEmptyUntilCustomerActuallyStartsRental() {
        final LockerOrder[] savedRef = new LockerOrder[1];
        when(userClient.getUser(44L)).thenReturn(ApiResponse.ok(new UserSummary(44L, "a@b.c", "0909", "User", "ACTIVE")));
        when(lockerClient.reserveBox(901L, null))
                .thenReturn(ApiResponse.ok(new LockerBoxSummary(5L, 901L, "CAB-05", 4, "RESERVED")));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> {
            LockerOrder saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(21L);
                saved.setOrderCode("ORD-21");
            }
            savedRef[0] = saved;
            return saved;
        });
        when(orderRepository.findById(21L)).thenAnswer(invocation -> Optional.ofNullable(savedRef[0]));

        var response = orderService.createRental(new RentalOrderRequest(5L, 901L, "STANDARD", 4, null, null), 44L);

        assertNull(response.pickupDeadline());
    }

    @Test
    void confirmAllowsUnpaidRentalAndStartsDeadlineFromConfirmTime() {
        LockerOrder order = new LockerOrder();
        order.setId(22L);
        order.setUserId(44L);
        order.setOrderCode("ORD-22");
        order.setType("RENTAL");
        order.setServiceCategory("RENTAL");
        order.setStatus("INITIALIZED");
        order.setSendBoxId(901L);
        order.setLockerId(5L);
        order.setPinCode("490912");
        order.setTotalPrice(BigDecimal.valueOf(20000));
        order.setOriginalPrice(BigDecimal.valueOf(20000));
        order.setPaymentStatus("UNPAID");
        order.setCreatedAt(LocalDateTime.now().minusHours(2));
        order.setPickupDeadline(LocalDateTime.now().plusHours(2));

        when(orderRepository.findById(22L)).thenReturn(Optional.of(order));
        when(lockerClient.occupyBox(901L))
                .thenReturn(ApiResponse.ok(new LockerBoxSummary(5L, 901L, "CAB-05", 4, "OCCUPIED")));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var before = LocalDateTime.now();
        var response = orderService.confirm(22L, 44L);
        var after = LocalDateTime.now();

        assertEquals("STORING", response.status());
        assertEquals("UNPAID", response.paymentStatus());
        assertTrue(response.pickupDeadline() != null && !response.pickupDeadline().isBefore(before.plusHours(4)));
        assertTrue(!response.pickupDeadline().isAfter(after.plusHours(4).plusSeconds(1)));
    }

    @Test
    void reorderUsesStoredRentalDurationHoursInsteadOfCreatedAtToDeadlineGap() {
        LockerOrder original = new LockerOrder();
        original.setId(23L);
        original.setUserId(44L);
        original.setType("RENTAL");
        original.setStatus("COMPLETED");
        original.setLockerId(5L);
        original.setSendBoxId(901L);
        original.setRentalDurationHours(4);
        original.setCustomerNote("Giữ đồ ngắn hạn");
        original.setCreatedAt(LocalDateTime.of(2026, 7, 25, 10, 0));
        original.setPickupDeadline(LocalDateTime.of(2026, 7, 25, 22, 0));

        final LockerOrder[] savedRef = new LockerOrder[1];
        when(orderRepository.findById(23L)).thenReturn(Optional.of(original));
        when(userClient.getUser(44L)).thenReturn(ApiResponse.ok(new UserSummary(44L, "a@b.c", "0909", "User", "ACTIVE")));
        when(lockerCellClient.getCell(901L)).thenReturn(ApiResponse.ok(
                new CellDto(901L, 1, "S", "STANDARD", 0, 0, "AVAILABLE", null)));
        when(lockerCellClient.findAvailable(5L, null, "STANDARD")).thenReturn(ApiResponse.ok(
                new CellDto(901L, 1, "S", "STANDARD", 0, 0, "AVAILABLE", null)));
        when(lockerClient.reserveBox(901L, null))
                .thenReturn(ApiResponse.ok(new LockerBoxSummary(5L, 901L, "CAB-05", 4, "RESERVED")));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> {
            LockerOrder saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(24L);
                saved.setOrderCode("ORD-24");
            }
            savedRef[0] = saved;
            return saved;
        });
        when(orderRepository.findById(24L)).thenAnswer(invocation -> Optional.ofNullable(savedRef[0]));

        var reordered = orderService.reorder(23L, 44L);

        assertNull(reordered.pickupDeadline());
        assertEquals(BigDecimal.valueOf(20000), reordered.totalPrice());
    }

    @Test
    void reorderClampsLegacyRentalDurationToPolicyMaximum() {
        LockerOrder original = new LockerOrder();
        original.setId(25L);
        original.setUserId(44L);
        original.setType("RENTAL");
        original.setStatus("COMPLETED");
        original.setLockerId(5L);
        original.setSendBoxId(901L);
        original.setCustomerNote("Đơn cũ");
        original.setCreatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        original.setPickupDeadline(LocalDateTime.of(2026, 8, 15, 0, 0));

        final LockerOrder[] savedRef = new LockerOrder[1];
        when(orderRepository.findById(25L)).thenReturn(Optional.of(original));
        when(userClient.getUser(44L)).thenReturn(ApiResponse.ok(new UserSummary(44L, "a@b.c", "0909", "User", "ACTIVE")));
        when(lockerCellClient.getCell(901L)).thenReturn(ApiResponse.ok(
                new CellDto(901L, 1, "S", "STANDARD", 0, 0, "AVAILABLE", null)));
        when(lockerCellClient.findAvailable(5L, null, "STANDARD")).thenReturn(ApiResponse.ok(
                new CellDto(901L, 1, "S", "STANDARD", 0, 0, "AVAILABLE", null)));
        when(lockerClient.reserveBox(901L, null))
                .thenReturn(ApiResponse.ok(new LockerBoxSummary(5L, 901L, "CAB-05", 4, "RESERVED")));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> {
            LockerOrder saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(26L);
                saved.setOrderCode("ORD-26");
            }
            savedRef[0] = saved;
            return saved;
        });
        when(orderRepository.findById(26L)).thenAnswer(invocation -> Optional.ofNullable(savedRef[0]));

        var reordered = orderService.reorder(25L, 44L);

        assertNull(reordered.pickupDeadline());
        assertEquals(BigDecimal.valueOf(5000L * 720), reordered.totalPrice());
    }
}
