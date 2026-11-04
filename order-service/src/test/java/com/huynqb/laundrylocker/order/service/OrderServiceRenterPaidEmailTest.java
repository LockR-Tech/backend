package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.*;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Đơn thuê ô vừa thanh toán: người thuê có email nhận ngay thư kèm mã mở ô và đủ thông tin lượt thuê.
@ExtendWith(MockitoExtension.class)
class OrderServiceRenterPaidEmailTest {

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

    private OrderService service() {
        return new OrderService(
                orderRepository, detailRepository, historyRepository, ratingRepository, complaintRepository,
                promotionRepository, promotionUsageRepository, promotionClaimRepository, rabbitTemplate,
                userClient, lockerClient, lockerCellClient, lockerLookupClient, notificationClient, qrTokenService,
                TestOrderRules.of(Map.of()), benefits, promotionViews, paidEffects);
    }

    @Test
    void paidRentalEmailsRenterTheUnlockCodeWithLockerBoxHoursAndAmount() {
        when(userClient.getUser(44L)).thenReturn(ApiResponse.ok(new UserSummary(
                44L, "thue@example.com", null, "Người Thuê", "ACTIVE", Set.of(), null, "vi")));
        when(lockerLookupClient.getLockers(any())).thenReturn(ApiResponse.ok(List.of(new LockerInfo(
                5L, 1L, "CAB-TU01", "Tủ Thư viện FPTU", "ACTIVE", "Tầng 1, Thư viện",
                null, null, false, null, 12, 3))));
        when(lockerClient.getBox(901L)).thenReturn(ApiResponse.ok(new LockerBoxSummary(5L, 901L, "CAB-TU01", 4, "BOOKED")));
        when(notificationClient.notifyGuest(any()))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(false, true, false, true)));

        service().notifyRenterOrderPaid(paidRental());

        ArgumentCaptor<GuestNotification.Request> sent = ArgumentCaptor.forClass(GuestNotification.Request.class);
        verify(notificationClient).notifyGuest(sent.capture());
        GuestNotification.Request mail = sent.getValue();
        assertNull(mail.phone());
        assertEquals("thue@example.com", mail.email());
        assertEquals("Thuê tủ thành công — mã mở ô ORD-RENT-1", mail.subject());
        assertTrue(mail.smsMessage() != null && !mail.smsMessage().isBlank(), "notification-service requires smsMessage");
        assertEquals("vi", mail.language());
        String body = mail.emailBody();
        assertTrue(body.startsWith("Xin chào Người Thuê,"), body);
        assertTrue(body.contains("Mã đơn: ORD-RENT-1"), body);
        assertTrue(body.contains("Tủ: Tủ Thư viện FPTU (Tầng 1, Thư viện)"), body);
        assertTrue(body.contains("Ô tủ: Ô số 4"), body);
        assertTrue(body.contains("Mã mở ô: 482913"), body);
        assertTrue(body.contains("Thời gian thuê: 3 giờ"), body);
        assertTrue(body.contains("Đã thanh toán: 15.000đ"), body);
        assertTrue(mail.emailHtml().contains("Mã mở ô của bạn: 482913"), mail.emailHtml());
    }

    @Test
    void renterWithoutEmailGetsNoMail() {
        when(userClient.getUser(44L)).thenReturn(ApiResponse.ok(new UserSummary(
                44L, null, "0909000000", "Người Thuê", "ACTIVE")));

        service().notifyRenterOrderPaid(paidRental());

        verify(notificationClient, never()).notifyGuest(any());
    }

    @Test
    void nonRentalOrRentalAlreadyStoringIsIgnored() {
        LockerOrder send = paidRental();
        send.setType("SEND");
        LockerOrder storing = paidRental();
        storing.setStatus("STORING");

        service().notifyRenterOrderPaid(send);
        service().notifyRenterOrderPaid(storing);

        verify(userClient, never()).getUser(any());
        verify(notificationClient, never()).notifyGuest(any());
    }

    private static LockerOrder paidRental() {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-RENT-1");
        order.setUserId(44L);
        order.setLockerId(5L);
        order.setSendBoxId(901L);
        order.setType("RENTAL");
        order.setStatus("INITIALIZED");
        order.setPaymentStatus("PAID");
        order.setPinCode("482913");
        order.setRentalDurationHours(3);
        order.setTotalPrice(new BigDecimal("15000"));
        order.setPaidAmount(new BigDecimal("15000"));
        return order;
    }
}
