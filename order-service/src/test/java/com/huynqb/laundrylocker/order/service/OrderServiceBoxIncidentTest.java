package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.dto.BoxIncidentNotice;
import com.huynqb.laundrylocker.order.dto.OrderResponse;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/// Ô của đơn đang có hàng bị báo sự cố: khách không lấy được đồ thì không bị tính phí quá giờ, không
/// bị bắt trả tiền / mở ô; KTV xử lý xong thì thời gian chờ được bù vào hạn lấy đồ.
@ExtendWith(MockitoExtension.class)
class OrderServiceBoxIncidentTest {

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
    @Mock private OrderBenefitService benefits;
    @Mock private PromotionViews promotionViews;
    @Mock private PaidOrderEffects paidEffects;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(
                orderRepository, detailRepository, historyRepository, ratingRepository, complaintRepository,
                promotionRepository, promotionUsageRepository, promotionClaimRepository, rabbitTemplate,
                userClient, lockerClient, lockerCellClient, lockerLookupClient, notificationClient, qrTokenService,
                TestOrderRules.of(Map.of(
                        "app.order.rental-rate-standard", 5000,
                        "app.order.rental-overtime-rate-percent", 100,
                        "app.order.box-incident-pickup-grace-hours", 2)),
                benefits, promotionViews, paidEffects);
        lenient().when(orderRepository.save(any(LockerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /// Đơn thuê đang trong tủ, đã trả 10.000đ tiền thuê.
    private LockerOrder storingRental(LocalDateTime deadline) {
        LockerOrder order = new LockerOrder();
        order.setId(96L);
        order.setUserId(44L);
        order.setOrderCode("ORD-20261016-5TCEI6");
        order.setType("RENTAL");
        order.setServiceCategory("RENTAL");
        order.setRentalCellType("STANDARD");
        order.setStatus("STORING");
        order.setSendBoxId(906L);
        order.setLockerId(2L);
        order.setPinCode("123456");
        order.setPickupDeadline(deadline);
        order.setTotalPrice(BigDecimal.valueOf(10000));
        order.setOriginalPrice(BigDecimal.valueOf(10000));
        order.setPaidAmount(BigDecimal.valueOf(10000));
        order.setPaymentStatus("PAID");
        when(orderRepository.findById(96L)).thenReturn(Optional.of(order));
        return order;
    }

    /// Khách tự báo ô của đơn mình: phí dừng ngay, nhưng ô mới chờ KTV kiểm tra nên khách vẫn mở ô được.
    @Test
    void reportingAFaultOnAStoredOrderPausesFeesButKeepsAccess() {
        LockerOrder order = storingRental(LocalDateTime.now().plusHours(3));
        when(lockerClient.reportFault(any(), any(), any())).thenReturn(ApiResponse.ok(Map.of("status", "PENDING_CHECK")));

        OrderResponse response = service.reportBoxFault(96L, 44L, "Ô không mở được");

        assertNotNull(order.getBoxIncidentOpenedAt());
        assertFalse(order.isBoxIncidentBlocksAccess());
        assertEquals("STORING", order.getStatus(), "Đơn có hàng không bị huỷ khi báo hỏng ô");
        assertNull(response.accessBlockReason());
        assertEquals("PICKUP", response.nextAction());
        assertNotNull(response.boxIncidentOpenedAt());
    }

    /// Ô đã được xác nhận hỏng (FAULT) mà khách báo thêm ⇒ khoá mở ô như cũ.
    @Test
    void reportingAnAlreadyConfirmedFaultBlocksAccess() {
        storingRental(LocalDateTime.now().plusHours(3));
        when(lockerClient.reportFault(any(), any(), any())).thenReturn(ApiResponse.ok(Map.of("status", "FAULT")));

        OrderResponse response = service.reportBoxFault(96L, 44L, "Ô không mở được");

        assertEquals(OrderAccessPolicy.BOX_INCIDENT_OPEN, response.accessBlockReason());
        assertEquals("WAIT_FOR_BOX_INCIDENT", response.nextAction());
    }

    /// Người khác báo ô đang chứa đồ của đơn: chưa xác nhận thì không đụng tới đơn; KTV xác nhận thì khoá.
    @Test
    void someoneElsesUnconfirmedReportLeavesTheOrderAlone() {
        LockerOrder order = storingRental(LocalDateTime.now().plusHours(3));

        service.boxIncidentOpened(96L, new BoxIncidentNotice(LocalDateTime.now(), false, 99L));
        assertNull(order.getBoxIncidentOpenedAt());

        service.boxIncidentOpened(96L, new BoxIncidentNotice(LocalDateTime.now().minusMinutes(5), true, 99L));
        assertNotNull(order.getBoxIncidentOpenedAt());
        assertTrue(order.isBoxIncidentBlocksAccess());
    }

    /// Đơn bị bỏ sót (sự cố chỉ được mở khi quét lại, sau khi đã cộng phí quá giờ nhiều ngày): báo lại với mốc
    /// là lúc tạo phiếu ⇒ dời mốc về đó và trả phần phí đã cộng sau mốc.
    @Test
    void anEarlierReportTimeMovesTheIncidentBackAndRefundsTheOvertime() {
        LocalDateTime now = LocalDateTime.now();
        LockerOrder order = storingRental(now.minusDays(6));
        order.setCreatedAt(now.minusDays(7));
        order.setBoxIncidentOpenedAt(now.minusHours(1));
        order.setBoxIncidentBlocksAccess(true);
        order.setExtraFee(BigDecimal.valueOf(155000));
        order.setTotalPrice(BigDecimal.valueOf(165000));
        order.setOriginalPrice(BigDecimal.valueOf(165000));
        order.setPaymentStatus("UNPAID");

        // Phiếu được tạo trước hạn lấy đồ.
        service.boxIncidentOpened(96L, new BoxIncidentNotice(now.minusDays(6).minusHours(4), true, 44L));

        assertEquals(now.minusDays(6).minusHours(4), order.getBoxIncidentOpenedAt());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getExtraFee()));
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getTotalPrice()));
        assertEquals("PAID", order.getPaymentStatus());
    }

    /// Báo sự cố qua màn tủ (không qua order-service): locker-service gọi sang; gọi lặp không dời mốc.
    @Test
    void lockerServiceCanOpenTheIncidentAndRepeatingKeepsTheFirstTime() {
        LockerOrder order = storingRental(LocalDateTime.now().plusHours(3));

        service.boxIncidentOpened(96L);
        LocalDateTime first = order.getBoxIncidentOpenedAt();
        service.boxIncidentOpened(96L);

        assertNotNull(first);
        assertEquals(first, order.getBoxIncidentOpenedAt());
    }

    /// Đơn gửi đã bỏ hàng: cả sendBoxId lẫn receiveBoxId là ô hỏng. Chuyển ô phải đổi cả hai, không thì lúc
    /// lấy hàng vẫn mở ô cũ và đơn vẫn bị tra ra là đang nằm trong ô cũ.
    @Test
    void relocatingMovesEveryBoxFieldThatPointsAtTheOldBox() {
        LockerOrder order = storingRental(LocalDateTime.now().plusHours(3));
        order.setType("SEND");
        order.setReceiveBoxId(906L);
        order.setBoxIncidentOpenedAt(LocalDateTime.now().minusMinutes(10));

        service.relocateBox(96L, 907L, 3);

        assertEquals(907L, order.getSendBoxId());
        assertEquals(907L, order.getReceiveBoxId());
        assertNull(order.getReservedBoxId());
        assertNull(order.getBoxIncidentOpenedAt());
    }

    @Test
    void overtimeStopsAtTheIncidentAndFeesChargedAfterItAreGivenBack() {
        LocalDateTime now = LocalDateTime.now();
        LockerOrder order = storingRental(now.minusHours(30));
        // Báo sự cố khi đã quá giờ 2 tiếng; đồng hồ cũ chạy tiếp và đã cộng 30 giờ phí vào giá.
        order.setBoxIncidentOpenedAt(now.minusHours(28));
        order.setExtraFee(BigDecimal.valueOf(150000));
        order.setTotalPrice(BigDecimal.valueOf(160000));
        order.setOriginalPrice(BigDecimal.valueOf(160000));
        order.setPaymentStatus("UNPAID");

        OrderResponse response = service.assessOvertime(96L, 44L);

        assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getExtraFee()), "Chỉ 2 giờ trước sự cố");
        assertEquals(0, BigDecimal.valueOf(20000).compareTo(order.getTotalPrice()));
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(response.amountDue()));
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(response.pickupOvertimeFee()));
    }

    @Test
    void overtimeChargedOnlyAfterTheIncidentLeavesTheOrderPaid() {
        LocalDateTime now = LocalDateTime.now();
        LockerOrder order = storingRental(now.minusHours(10));
        // Báo sự cố khi còn trong hạn ⇒ không giờ nào được tính phí.
        order.setBoxIncidentOpenedAt(now.minusHours(12));
        order.setExtraFee(BigDecimal.valueOf(50000));
        order.setTotalPrice(BigDecimal.valueOf(60000));
        order.setOriginalPrice(BigDecimal.valueOf(60000));
        order.setPaymentStatus("UNPAID");

        service.assessOvertime(96L, 44L);

        assertEquals(0, BigDecimal.ZERO.compareTo(order.getExtraFee()));
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getTotalPrice()));
        assertEquals("PAID", order.getPaymentStatus());
    }

    @Test
    void resolvingTheIncidentAddsTheStuckTimeToTheDeadline() {
        LocalDateTime now = LocalDateTime.now();
        // Báo sự cố lúc còn 4 giờ thuê; KTV xử lý sau 6 ngày.
        LocalDateTime openedAt = now.minusDays(6);
        LockerOrder order = storingRental(openedAt.plusHours(4));
        order.setBoxIncidentOpenedAt(openedAt);

        OrderResponse response = service.resumeAfterBoxIncident(96L, null, "KTV khắc phục sự cố tại ô #6");

        assertNull(order.getBoxIncidentOpenedAt());
        long minutesLeft = Duration.between(now, order.getPickupDeadline()).toMinutes();
        assertTrue(minutesLeft >= 4 * 60 - 1 && minutesLeft <= 4 * 60 + 1, "Còn đúng 4 giờ như lúc báo: " + minutesLeft);
        assertNull(response.accessBlockReason());
        assertEquals("PICKUP", response.nextAction());
        assertNull(response.boxIncidentOpenedAt());
    }

    @Test
    void resolvingGivesAtLeastTheConfiguredGraceToComeBack() {
        LocalDateTime now = LocalDateTime.now();
        // Báo sự cố khi đã quá giờ ⇒ bù thời gian vẫn còn quá hạn; tối thiểu 2 giờ ân hạn.
        LocalDateTime openedAt = now.minusHours(5);
        LockerOrder order = storingRental(openedAt.minusHours(1));
        order.setBoxIncidentOpenedAt(openedAt);

        service.resumeAfterBoxIncident(96L, null, null);

        long minutesLeft = Duration.between(now, order.getPickupDeadline()).toMinutes();
        assertTrue(minutesLeft >= 2 * 60 - 1, "Ít nhất 2 giờ để tới lấy: " + minutesLeft);
    }

    @Test
    void resolvingTwiceIsHarmless() {
        LocalDateTime deadline = LocalDateTime.now().plusHours(3);
        LockerOrder order = storingRental(deadline);

        service.resumeAfterBoxIncident(96L, null, null);

        assertEquals(deadline, order.getPickupDeadline());
    }

    @Test
    void extendingARentalIsRefusedWhileTheBoxIsBeingFixed() {
        LockerOrder order = storingRental(LocalDateTime.now().minusHours(1));
        order.setBoxIncidentOpenedAt(LocalDateTime.now().minusHours(2));

        BusinessException error = assertThrows(BusinessException.class, () -> service.extendRental(96L, 44L, 2));

        assertEquals("ORDER_BOX_INCIDENT_OPEN", error.getCode());
    }

    /// Tự bấm "Tôi đã lấy đồ" / "Kết thúc thuê" (app, kiosk đăng nhập OTP) lúc đồ còn kẹt sẽ đóng đơn và
    /// nhả ô cho khách khác trong khi đồ vẫn bên trong.
    @Test
    void customerCannotCloseTheOrderWhileItemsAreStuck() {
        LockerOrder order = storingRental(LocalDateTime.now().plusHours(3));
        order.setBoxIncidentOpenedAt(LocalDateTime.now().minusMinutes(30));
        order.setBoxIncidentBlocksAccess(true);

        assertEquals("ORDER_BOX_INCIDENT_OPEN",
                assertThrows(BusinessException.class, () -> service.complete(96L, 44L)).getCode());
        assertEquals("ORDER_BOX_INCIDENT_OPEN",
                assertThrows(BusinessException.class, () -> service.pickupStorage(96L, 44L)).getCode());
        assertEquals("STORING", order.getStatus());
    }

    @Test
    void overdueSweepLeavesStuckOrdersInTheBox() {
        LockerOrder order = new LockerOrder();
        order.setId(97L);
        order.setType("SEND");
        order.setStatus("STORING");
        order.setSendBoxId(906L);
        order.setPickupDeadline(LocalDateTime.now().minusDays(10));
        order.setBoxIncidentOpenedAt(LocalDateTime.now().minusDays(9));
        order.setTotalPrice(BigDecimal.valueOf(15000));
        when(orderRepository.findByStatusOrderByCreatedAtDesc("STORING")).thenReturn(List.of(order));
        when(orderRepository.findByStatusOrderByCreatedAtDesc("RETURNED")).thenReturn(List.of());

        Map<String, Object> result = service.releaseOverdueOrders();

        assertEquals(0, result.get("expiredOrders"));
        assertEquals("STORING", order.getStatus());
        assertEquals(906L, order.getSendBoxId());
    }
}
