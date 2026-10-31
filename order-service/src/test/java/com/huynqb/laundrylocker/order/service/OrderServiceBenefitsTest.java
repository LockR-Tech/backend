package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.dto.ReceiverOrderEmailResponse;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.LoyaltyClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.LoyaltyRedemption;
import com.huynqb.laundrylocker.order.dto.OrderResponse;
import com.huynqb.laundrylocker.order.dto.PromotionRequest;
import com.huynqb.laundrylocker.order.dto.PromotionView;
import com.huynqb.laundrylocker.order.dto.SendOrderRequest;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.Promotion;
import com.huynqb.laundrylocker.order.model.PromotionClaim;
import com.huynqb.laundrylocker.order.model.PromotionUsage;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// OrderService với ưu đãi thật (OrderBenefitService + PaidOrderEffects): đơn gửi hàng dùng mã + điểm, đơn 0đ
/// coi như đã trả, huỷ/hoàn tiền trả lại ưu đãi, và API mã giảm giá theo tủ.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceBenefitsTest {

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
        OrderRules rules = TestOrderRules.of(Map.of("app.order.send-base-fee", 15000));
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
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(lockerLookupClient.getLockers(any())).thenReturn(ApiResponse.ok(List.of(
                OrderBenefitServiceTest.locker(7L, "TU07", "Tủ Thư viện"))));
    }

    @Test
    void sendOrderWithVoucherAndPointsChargesOnlyTheRest() {
        when(promotionRepository.findByCodeForUpdate("GIAM5K")).thenReturn(Optional.of(promotion(1L, "GIAM5K", "5000", null)));
        when(loyaltyClient.redeem(any())).thenReturn(ApiResponse.ok(new LoyaltyRedemption.Result(30, new BigDecimal("3000"))));

        OrderResponse response = orderService.createSend(send("giam5k", 30), 44L);

        assertEquals(0, new BigDecimal("15000").compareTo(response.originalPrice()));
        assertEquals(0, new BigDecimal("5000").compareTo(response.discount()));
        assertEquals(30, response.pointsUsed());
        assertEquals(0, new BigDecimal("3000").compareTo(response.pointsDiscount()));
        assertEquals(0, new BigDecimal("7000").compareTo(response.totalPrice()));
        assertEquals(0, new BigDecimal("7000").compareTo(response.amountDue()));
        assertEquals("UNPAID", response.paymentStatus());
        verify(loyaltyClient).redeem(new LoyaltyRedemption.Request(44L, 21L, new BigDecimal("10000"), 30));
        verify(dronePickupService, never()).startForPaidOrder(anyLong());
    }

    @Test
    void sendOrderLinksExistingReceiverAccountByEmail() {
        when(userClient.getUserByEmail("Receiver@gmail.com"))
                .thenReturn(ApiResponse.ok(new UserSummary(
                        88L, "receiver@gmail.com", "0901234567", "Nguoi Nhan", "ACTIVE")));
        SendOrderRequest request = new SendOrderRequest(
                5L, 301L, null, "0901234567", "Nguoi Nhan", " Receiver@gmail.com ", null,
                null, null, null);

        orderService.createSend(request, 44L);

        assertEquals("Receiver@gmail.com", stored.get().getReceiverEmail());
        assertEquals(88L, stored.get().getReceiverUserId());
        assertEquals(88L, stored.get().getReceiverId());
        verify(userClient).getUserByEmail("Receiver@gmail.com");
        verify(notificationClient).requestNotification(org.mockito.ArgumentMatchers.argThat(
                notification -> notification.userId().equals(88L)
                        && notification.type().equals("ORDER_CREATED")));
    }

    @Test
    void orderFullyCoveredByDiscountsIsPaidImmediately() {
        when(promotionRepository.findByCodeForUpdate("FREE")).thenReturn(Optional.of(promotion(1L, "FREE", "10000", null)));
        when(loyaltyClient.redeem(any())).thenReturn(ApiResponse.ok(new LoyaltyRedemption.Result(50, new BigDecimal("5000"))));

        OrderResponse response = orderService.createSend(send("FREE", 50), 44L);

        assertEquals(0, BigDecimal.ZERO.compareTo(response.totalPrice()));
        assertEquals("PAID", response.paymentStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.paidAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.amountDue()));
        LockerOrder order = stored.get();
        assertEquals("PAID", order.getPaymentStatus());
        assertTrue(order.getPaidAt() != null);
        // Cùng các bước như payment.completed: báo "thanh toán thành công".
        verify(notificationClient).requestNotification(org.mockito.ArgumentMatchers.argThat(
                n -> "PAYMENT_COMPLETED".equals(n.type())));

        // Job tự huỷ đơn chưa thanh toán không đụng tới đơn này (dùng hạn của đơn đã trả).
        order.setCreatedAt(LocalDateTime.now().minusHours(2));
        when(orderRepository.findByStatusOrderByCreatedAtDesc("INITIALIZED")).thenReturn(List.of(order));
        assertEquals(0, orderService.autoCancelUnconfirmedOrders().get("canceledOrders"));
        assertEquals("INITIALIZED", order.getStatus());
    }

    @Test
    void lockerScopedVoucherCannotBeUsedAtAnotherLocker() {
        when(promotionRepository.findByCodeForUpdate("TUTV")).thenReturn(Optional.of(promotion(2L, "TUTV", "3000", 7L)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> orderService.createSend(send("TUTV", null), 44L));

        assertEquals("PROMOTION_INVALID", error.getCode());
        assertTrue(error.getMessage().contains("Tủ Thư viện"), error.getMessage());
    }

    @Test
    void cancelingReturnsVoucherAndPoints() {
        LockerOrder order = existingOrder("INITIALIZED");
        order.setPointsUsed(30);
        order.setPointsDiscount(new BigDecimal("3000"));
        PromotionUsage usage = usage();
        Promotion voucher = promotion(1L, "GIAM5K", "5000", null);
        voucher.setUsageCount(1);
        when(promotionUsageRepository.findByOrderId(21L)).thenReturn(List.of(usage));
        when(promotionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(voucher));
        when(loyaltyClient.refund(21L)).thenReturn(ApiResponse.ok(new LoyaltyRedemption.Refund(30)));

        orderService.cancel(21L, null, 44L);

        assertEquals("CANCELED", order.getStatus());
        assertEquals(0, voucher.getUsageCount());
        verify(promotionUsageRepository).delete(usage);
        verify(loyaltyClient).refund(21L);
        verify(orderRepository).markPointsRefunded(eq(21L), any());
    }

    @Test
    void adminCancelThroughStatusUpdateAlsoReturnsBenefits() {
        LockerOrder order = existingOrder("INITIALIZED");
        order.setPointsUsed(10);
        order.setPinCode("123456");
        when(promotionUsageRepository.findByOrderId(21L)).thenReturn(List.of());

        orderService.updateStatus(21L, "CANCELED", null);

        assertEquals("CANCELED", order.getStatus());
        // Admin huỷ đi cùng đường với khách huỷ: PIN cũ không còn mở được ô.
        assertNull(order.getPinCode());
        verify(loyaltyClient).refund(21L);
    }

    @Test
    void adminCannotReopenOrSkipStepsThroughStatusUpdate() {
        existingOrder("COMPLETED");
        assertEquals("ORDER_STATUS_TRANSITION_INVALID", assertThrows(BusinessException.class,
                () -> orderService.updateStatus(21L, "INITIALIZED", null))
                .getCode());

        existingOrder("INITIALIZED");
        // STORING chỉ đến qua confirm() (cấp PIN người nhận, giữ ô).
        assertEquals("ORDER_STATUS_TRANSITION_INVALID", assertThrows(BusinessException.class,
                () -> orderService.updateStatus(21L, "STORING", null))
                .getCode());

        existingOrder("STORING");
        // Đồ còn trong ô: admin chốt đơn hoặc dời kho, không huỷ.
        assertEquals("ORDER_STATUS_TRANSITION_INVALID", assertThrows(BusinessException.class,
                () -> orderService.updateStatus(21L, "CANCELED", null))
                .getCode());
        verify(lockerClient, never()).releaseBox(anyLong());
    }

    @Test
    void adminExpireMovesOrderToStorageAndReleasesCell() {
        LockerOrder order = existingOrder("STORING");
        order.setReceiveBoxId(301L);
        order.setPinCode("123456");

        orderService.updateStatus(21L, "EXPIRED", 9L);

        assertEquals("EXPIRED", order.getStatus());
        assertNull(order.getReceiveBoxId());
        assertNull(order.getPinCode());
        verify(lockerClient).releaseBox(301L);
    }

    @Test
    void adminCannotCancelDroneOrderAfterParcelPickedUp() {
        LockerOrder order = existingOrder("AWAITING_DISPATCH");
        order.setType("DRONE_DELIVERY");
        order.setDeliveryStage("PICKED_UP");

        assertEquals("DRONE_ORDER_STATUS_INVALID", assertThrows(BusinessException.class,
                () -> orderService.updateStatus(21L, "CANCELED", null))
                .getCode());
        assertEquals("AWAITING_DISPATCH", order.getStatus());
    }

    @Test
    void resetPinOnlyWhileOrderStillUsesItsPin() {
        LockerOrder order = existingOrder("COMPLETED");
        order.setPinCode(null);
        assertEquals("ORDER_STATUS_INVALID", assertThrows(BusinessException.class,
                () -> orderService.resetPin(21L, 44L)).getCode());

        order = existingOrder("STORING");
        order.setPinCode("123456");
        orderService.resetPin(21L, 44L);
        assertTrue(order.getPinCode() != null && !"123456".equals(order.getPinCode()));
    }

    @Test
    void refundedPaymentReturnsBenefitsOnce() {
        LockerOrder order = existingOrder("COMPLETED");
        order.setPaymentStatus("REFUND_PENDING");
        order.setPointsUsed(10);
        when(promotionUsageRepository.findByOrderId(21L)).thenReturn(List.of());

        orderService.updatePaymentStatus(21L, "REFUNDED", "Đã hoàn", 1L);
        assertEquals("REFUNDED", order.getPaymentStatus());
        verify(loyaltyClient).refund(21L);

        // Thông báo REFUNDED lặp lại không hoàn thêm.
        orderService.updatePaymentStatus(21L, "REFUNDED", null, 1L);
        verify(loyaltyClient).refund(21L);
    }

    @Test
    void validateReportsLockerAndMinimumOrder() {
        Promotion scoped = promotion(2L, "TUTV", "3000", 7L);
        scoped.setMinOrderAmount(new BigDecimal("20000"));
        when(promotionRepository.findByCodeIgnoreCase(any())).thenReturn(Optional.of(scoped));

        Map<String, Object> missingLocker = orderService.validatePromotion("tutv", null, 44L, null, true);
        assertEquals(false, missingLocker.get("valid"));
        assertTrue(((String) missingLocker.get("reason")).contains("Tủ Thư viện"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) missingLocker.get("promotion");
        assertEquals(7L, summary.get("lockerId"));
        assertEquals("TU07", summary.get("lockerCode"));
        assertEquals("Tủ Thư viện", summary.get("lockerName"));

        Map<String, Object> belowMinimum = orderService.validatePromotion("tutv", 7L, 44L, new BigDecimal("15000"), true);
        assertEquals(false, belowMinimum.get("valid"));
        assertTrue(((String) belowMinimum.get("reason")).contains("tối thiểu"));

        assertEquals(true, orderService.validatePromotion("tutv", 7L, 44L, new BigDecimal("20000"), true).get("valid"));
        // Admin tra mã không kiểm tủ.
        assertEquals(true, orderService.validatePromotion("tutv").get("valid"));
    }

    @Test
    void activeVouchersAtALockerAreGlobalPlusThatLocker() {
        Promotion global = promotion(1L, "ALL", "1000", null);
        Promotion here = promotion(2L, "TUTV", "3000", 7L);
        Promotion elsewhere = promotion(3L, "TU9", "3000", 9L);
        when(promotionRepository.findByStatus("ACTIVE")).thenReturn(List.of(global, here, elsewhere));

        List<PromotionView> atLocker = orderService.activePromotions(7L);
        assertEquals(List.of("ALL", "TUTV"), atLocker.stream().map(PromotionView::code).toList());
        PromotionView scoped = atLocker.get(1);
        assertEquals("TU07", scoped.lockerCode());
        assertEquals("Tủ Thư viện", scoped.lockerName());
        assertNull(atLocker.get(0).lockerName());
        // Bản cho khách không lộ người tạo mã.
        assertNull(scoped.createdByUserId());

        assertEquals(3, orderService.activePromotions(null).size());
    }

    @Test
    void myVouchersFilterByLockerAndCarryLockerName() {
        PromotionClaim globalClaim = claim(11L, 1L);
        PromotionClaim elsewhereClaim = claim(12L, 3L);
        when(promotionClaimRepository.findByUserIdOrderByCreatedAtDesc(44L)).thenReturn(List.of(globalClaim, elsewhereClaim));
        when(promotionRepository.findAllById(any())).thenReturn(List.of(
                promotion(1L, "ALL", "1000", null), promotion(3L, "TU9", "3000", 9L)));

        List<Map<String, Object>> atLocker7 = orderService.myVouchers(44L, null, 7L);
        assertEquals(1, atLocker7.size());
        assertEquals("ALL", atLocker7.get(0).get("code"));
        assertTrue(atLocker7.get(0).containsKey("lockerName"));

        assertEquals(2, orderService.myVouchers(44L, null, null).size());
    }

    @Test
    void adminFiltersByLockerAndChecksTheLockerExists() {
        Promotion global = promotion(1L, "ALL", "1000", null);
        Promotion here = promotion(2L, "TUTV", "3000", 7L);
        when(promotionRepository.findAll()).thenReturn(List.of(global, here));

        assertEquals(List.of("TUTV"), orderService.promotions(null, null, 7L, null).stream().map(PromotionView::code).toList());
        assertEquals(List.of("ALL"), orderService.promotions(null, null, 0L, null).stream().map(PromotionView::code).toList());
        assertEquals(List.of("ALL"), orderService.promotions(null, null, null, "GLOBAL").stream().map(PromotionView::code).toList());
        PromotionView adminView = orderService.promotions(null, null, 7L, null).get(0);
        assertEquals("Tủ Thư viện", adminView.lockerName());

        when(lockerLookupClient.getLockers(List.of(99L))).thenReturn(ApiResponse.ok(List.of()));
        assertEquals("PROMOTION_LOCKER_NOT_FOUND", assertThrows(BusinessException.class,
                () -> orderService.createPromotion(promotionRequest(99L), 1L)).getCode());

        // Cập nhật giữ đúng giá trị client gửi: null = chuyển về toàn hệ thống.
        when(promotionRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(here));
        PromotionView updated = orderService.updatePromotion(2L, promotionRequest(null));
        assertNull(updated.lockerId());
        assertNull(here.getLockerId());
        assertFalse(updated.stackable());
    }

    @Test
    void sendOrderRequiresAValidReceiverEmail() {
        for (String email : new String[] {null, "  "}) {
            BusinessException ex = assertThrows(BusinessException.class, () -> orderService.createSend(
                    new SendOrderRequest(5L, 301L, null, "0901234567", "Nhận", email, null, null, null, null), 44L));
            assertEquals("RECEIVER_EMAIL_REQUIRED", ex.getCode());
        }
        BusinessException invalid = assertThrows(BusinessException.class, () -> orderService.createSend(
                new SendOrderRequest(5L, 301L, null, "0901234567", "Nhận", "nhan@", null, null, null, null), 44L));
        assertEquals("RECEIVER_EMAIL_INVALID", invalid.getCode());
        verify(orderRepository, never()).save(any(LockerOrder.class));
    }

    @Test
    void senderResendsPickupCodeToReceiverEmailOncePerMinute() {
        LockerOrder order = existingOrder("STORING");
        order.setPinCode("795296");
        order.setReceiverEmail("nhan@example.com");
        when(notificationClient.notifyGuest(any()))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(false, true, false, true)));

        ReceiverOrderEmailResponse result = orderService.sendReceiverEmail(21L, 44L, null);

        assertTrue(result.includesPickupCode());
        assertEquals("nh***@example.com", result.maskedEmail());
        org.mockito.ArgumentCaptor<GuestNotification.Request> sent =
                org.mockito.ArgumentCaptor.forClass(GuestNotification.Request.class);
        verify(notificationClient).notifyGuest(sent.capture());
        assertEquals("nhan@example.com", sent.getValue().email());
        assertNull(sent.getValue().phone());
        assertTrue(sent.getValue().emailBody().contains("Mã mở tủ: 795296"));

        BusinessException tooSoon = assertThrows(BusinessException.class,
                () -> orderService.sendReceiverEmail(21L, 44L, null));
        assertEquals("RECEIVER_EMAIL_TOO_SOON", tooSoon.getCode());
    }

    @Test
    void beforeDropSenderOnlySavesTheNewReceiverEmail() {
        existingOrder("INITIALIZED").setPinCode("111222");

        ReceiverOrderEmailResponse result = orderService.sendReceiverEmail(21L, 44L, " moi@example.com ");

        assertFalse(result.includesPickupCode());
        assertEquals("moi@example.com", stored.get().getReceiverEmail());
        verify(notificationClient, never()).notifyGuest(any());
    }

    @Test
    void oldOrderWithoutEmailAsksForOne() {
        existingOrder("STORING").setPinCode("795296");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.sendReceiverEmail(21L, 44L, null));
        assertEquals("RECEIVER_EMAIL_MISSING", ex.getCode());
    }

    @Test
    void onlyTheSenderCanResendTheReceiverEmail() {
        LockerOrder order = existingOrder("STORING");
        order.setPinCode("795296");
        order.setReceiverEmail("nhan@example.com");

        assertThrows(RuntimeException.class, () -> orderService.sendReceiverEmail(21L, 99L, null));
        verify(notificationClient, never()).notifyGuest(any());
    }

    private LockerOrder existingOrder(String status) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(44L);
        order.setType("SEND");
        order.setStatus(status);
        order.setLockerId(5L);
        order.setOriginalPrice(new BigDecimal("15000"));
        order.setDiscount(new BigDecimal("5000"));
        order.setTotalPrice(new BigDecimal("7000"));
        order.setPaymentStatus("UNPAID");
        stored.set(order);
        return order;
    }

    private static SendOrderRequest send(String promotionCode, Integer pointsToUse) {
        return new SendOrderRequest(5L, 301L, null, "0901234567", "Người nhận", "nhan@example.com", null, null,
                promotionCode, pointsToUse);
    }

    private static Promotion promotion(Long id, String code, String fixedAmount, Long lockerId) {
        Promotion promotion = new Promotion();
        promotion.setId(id);
        promotion.setCode(code);
        promotion.setName(code);
        promotion.setDiscountType("FIXED_AMOUNT");
        promotion.setDiscountValue(new BigDecimal(fixedAmount));
        promotion.setLockerId(lockerId);
        promotion.setStatus("ACTIVE");
        promotion.setCreatedByUserId(1L);
        return promotion;
    }

    private static PromotionUsage usage() {
        PromotionUsage usage = new PromotionUsage();
        usage.setPromotionId(1L);
        usage.setUserId(44L);
        usage.setOrderId(21L);
        return usage;
    }

    private static PromotionClaim claim(Long id, Long promotionId) {
        PromotionClaim claim = new PromotionClaim();
        claim.setId(id);
        claim.setPromotionId(promotionId);
        claim.setUserId(44L);
        return claim;
    }

    private static PromotionRequest promotionRequest(Long lockerId) {
        return new PromotionRequest("TUTV", "Ưu đãi tủ", "FIXED_AMOUNT", new BigDecimal("3000"), null, null,
                null, "ACTIVE", null, null, lockerId, null, null, null, null);
    }
}
