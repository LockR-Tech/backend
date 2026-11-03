package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.LoyaltyClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.OrderResponse;
import com.huynqb.laundrylocker.order.dto.SendOrderRequest;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.*;
import com.huynqb.laundrylocker.order.settings.OrderRules;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/// Gửi hàng qua tủ: người gửi chọn số giờ giữ hàng (phí gửi đã gồm 48 giờ, giờ vượt tính như thuê ô) và gia hạn
/// sau khi bỏ hàng — gia hạn của đơn đã trả chỉ có hiệu lực khi trả tiền, chưa trả thì người nhận vẫn lấy hàng.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceSendStorageTest {

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
    @Mock private LoyaltyClient loyaltyClient;
    @Mock private DronePickupService dronePickupService;

    private OrderService orderService;
    private final AtomicReference<LockerOrder> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        // 48 giờ đã gồm trong 15.000đ; giờ vượt: 5.000đ/giờ, trần 50.000đ/ngày.
        OrderRules rules = TestOrderRules.of(Map.of(
                "app.order.send-base-fee", 15000,
                "app.order.send-pickup-hours-limit", 48,
                "app.order.send-storage-max-hours", 168,
                "app.order.rental-rate-standard", 5000,
                "app.order.rental-daily-rate-standard", 50000));
        PromotionViews views = new PromotionViews(lockerLookupClient);
        OrderBenefitService benefits = new OrderBenefitService(
                promotionRepository, promotionUsageRepository, promotionClaimRepository, orderRepository,
                loyaltyClient, views, rules);
        PaidOrderEffects paidEffects =
                new PaidOrderEffects(rabbitTemplate, notificationClient, dronePickupService, orderRepository);
        orderService = new OrderService(
                orderRepository, detailRepository, historyRepository, ratingRepository, complaintRepository,
                promotionRepository, promotionUsageRepository, promotionClaimRepository, rabbitTemplate,
                userClient, lockerClient, lockerCellClient, lockerLookupClient, notificationClient, qrTokenService,
                rules, benefits, views, paidEffects);

        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(inv -> {
            LockerOrder order = inv.getArgument(0);
            if (order.getId() == null) {
                order.setId(21L);
            }
            stored.set(order);
            return order;
        });
        when(orderRepository.findById(21L)).thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(orderRepository.findByIdForUpdate(21L)).thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(lockerLookupClient.getLockers(any())).thenReturn(ApiResponse.ok(List.of(
                OrderBenefitServiceTest.locker(7L, "TU07", "Tủ Thư viện"))));
    }

    @Test
    void storageHoursAbove48AreChargedLikeRentingTheCell() {
        OrderResponse response = orderService.createSend(send(72), 44L);

        // 15.000 + 24 giờ vượt (5.000 × 24 = 120.000, trần ngày 50.000).
        assertEquals(0, new BigDecimal("65000").compareTo(response.totalPrice()));
        assertEquals(72, stored.get().getRentalDurationHours());
    }

    @Test
    void appWithoutHoursKeepsTheIncluded48AndFlatFee() {
        OrderResponse response = orderService.createSend(send(null), 44L);

        assertEquals(0, new BigDecimal("15000").compareTo(response.totalPrice()));
        assertEquals(48, stored.get().getRentalDurationHours());
    }

    @Test
    void hoursOutsideIncludedToMaxAreRejected() {
        BusinessException tooShort = assertThrows(BusinessException.class, () -> orderService.createSend(send(24), 44L));
        assertEquals("SEND_STORAGE_HOURS_INVALID", tooShort.getCode());
        assertThrows(BusinessException.class, () -> orderService.createSend(send(200), 44L));
    }

    @Test
    void paidExtensionWaitsForPaymentAndDoesNotBlockTheReceiver() {
        LocalDateTime deadline = LocalDateTime.now().plusHours(10).truncatedTo(ChronoUnit.SECONDS);
        storingPaidOrder(deadline);

        OrderResponse extended = orderService.extendSend(21L, 44L, 24);

        assertEquals(24, extended.pendingExtensionHours());
        assertEquals(0, new BigDecimal("50000").compareTo(extended.pendingExtensionFee()));
        assertEquals(0, new BigDecimal("50000").compareTo(extended.amountDue()));
        assertEquals(deadline, stored.get().getPickupDeadline());
        assertEquals(48, stored.get().getRentalDurationHours());

        // Người nhận lấy hàng khi người gửi chưa trả gia hạn: được, và phần gia hạn bị bỏ.
        OrderResponse completed = orderService.complete(21L, 44L);
        assertEquals("COMPLETED", completed.status());
        assertEquals("PAID", completed.paymentStatus());
        assertEquals(0, new BigDecimal("15000").compareTo(completed.totalPrice()));
        assertNull(stored.get().getPendingExtensionHours());
    }

    @Test
    void paymentAppliesTheExtensionToTheDeadline() {
        LocalDateTime deadline = LocalDateTime.now().plusHours(10).truncatedTo(ChronoUnit.SECONDS);
        storingPaidOrder(deadline);
        orderService.extendSend(21L, 44L, 24);

        LockerOrder order = stored.get();
        order.setPaymentStatus("PAID");
        order.setPaidAmount(order.getTotalPrice());
        orderService.applyPaidSendExtension(order);

        assertEquals(deadline.plusHours(24), stored.get().getPickupDeadline());
        assertEquals(72, stored.get().getRentalDurationHours());
        assertNull(stored.get().getPendingExtensionHours());
    }

    @Test
    void secondExtensionWaitsForThePreviousPayment() {
        storingPaidOrder(LocalDateTime.now().plusHours(10));
        orderService.extendSend(21L, 44L, 24);

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.extendSend(21L, 44L, 12));
        assertEquals("SEND_EXTENSION_PENDING", ex.getCode());
    }

    @Test
    void unpaidOrderIsExtendedRightAwayInThePrice() {
        orderService.createSend(send(null), 44L);

        OrderResponse extended = orderService.extendSend(21L, 44L, 24);

        assertNull(extended.pendingExtensionHours());
        assertEquals(72, stored.get().getRentalDurationHours());
        assertEquals(0, new BigDecimal("65000").compareTo(extended.totalPrice()));
    }

    @Test
    void pendingExtensionOnPaidInitializedOrderStillAllowsDropOff() {
        orderService.createSend(send(null), 44L);
        LockerOrder order = stored.get();
        order.setPaymentStatus("PAID");
        order.setPaidAmount(order.getTotalPrice());
        order.setDropOpenedAt(LocalDateTime.now());

        orderService.extendSend(21L, 44L, 24);
        OrderResponse dropped = orderService.confirm(21L, 44L);

        assertEquals("STORING", dropped.status());
        assertNull(dropped.accessBlockReason());
        assertEquals(24, dropped.pendingExtensionHours());
    }

    private void storingPaidOrder(LocalDateTime deadline) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(44L);
        order.setLockerId(5L);
        order.setSendBoxId(301L);
        order.setType("SEND");
        order.setServiceCategory("PARCEL");
        order.setStatus("STORING");
        order.setRentalDurationHours(48);
        order.setPickupDeadline(deadline);
        order.setPinCode("123456");
        order.setOriginalPrice(new BigDecimal("15000"));
        order.setTotalPrice(new BigDecimal("15000"));
        order.setPaidAmount(new BigDecimal("15000"));
        order.setPaymentStatus("PAID");
        order.setPaidAt(LocalDateTime.now().minusHours(1));
        order.setCreatedAt(LocalDateTime.now().minusHours(2));
        stored.set(order);
    }

    private static SendOrderRequest send(Integer storageHours) {
        return new SendOrderRequest(5L, 301L, null, "0901234567", "Người nhận", "nhan@example.com", null, null,
                null, null, storageHours);
    }
}
