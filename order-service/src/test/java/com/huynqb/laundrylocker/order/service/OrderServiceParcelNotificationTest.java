package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.dto.NotificationRequest;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.util.BusinessTime;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Thông báo khi người gửi xác nhận đã bỏ hàng vào tủ: người nhận (app/SMS/email) và
/// bản tóm tắt cho người gửi — tiếng Việt, có tên tủ + ô, giờ Việt Nam.
@ExtendWith(MockitoExtension.class)
class OrderServiceParcelNotificationTest {

    private static final String PIN_PATTERN = "\\d{6}";

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

    private OrderService service(boolean sms, boolean email) {
        OrderService service = new OrderService(
                orderRepository, detailRepository, historyRepository, ratingRepository, complaintRepository,
                promotionRepository, promotionUsageRepository, promotionClaimRepository, rabbitTemplate,
                userClient, lockerClient, lockerCellClient, lockerLookupClient, notificationClient, qrTokenService,
                TestOrderRules.of(Map.of(
                        "app.order.require-payment-before-drop", true,
                        "app.order.send-confirm-requires-open", true,
                        "app.order.receiver-notify-sms", sms,
                        "app.order.receiver-notify-email", email)), benefits, promotionViews, paidEffects);
        // Cột DB lưu giờ UTC như container production, bất kể múi giờ máy chạy test.
        service.setTime(new BusinessTime(ZoneOffset.UTC, BusinessTime.DEFAULT_BUSINESS_ZONE, Clock.systemUTC()));
        return service;
    }

    @Test
    void receiverAndSenderGetVietnameseMessagesWithLockerBoxAndLocalDeadline() {
        OrderService service = service(true, false);
        LockerOrder order = storedSendOrder();
        when(orderRepository.findById(33L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lockerLookupClient.getLockers(any())).thenReturn(ApiResponse.ok(List.of(new LockerInfo(
                5L, 1L, "CAB-TU01", "Tủ Thư viện FPTU", "ACTIVE", "Tầng 1, Thư viện",
                null, null, false, null, 12, 3))));
        when(lockerClient.getBox(901L)).thenReturn(ApiResponse.ok(new LockerBoxSummary(5L, 901L, "CAB-TU01", 4, "OCCUPIED")));
        when(userClient.getUserByPhone("0909000000"))
                .thenReturn(ApiResponse.ok(new UserSummary(77L, "nhan@example.com", "0909000000", "Người Nhận", "ACTIVE")));
        when(notificationClient.notifyGuest(any()))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(true, false, true, false)));

        service.confirm(33L, 44L);

        String deadline = service.formatDeadline(order.getPickupDeadline());
        assertTrue(deadline.matches("\\d{2}:\\d{2} \\d{2}/\\d{2}/\\d{4}"), deadline);

        List<NotificationRequest> sent = capturedNotifications();
        NotificationRequest receiver = byType(sent, "ORDER_PARCEL_READY");
        assertEquals(77L, receiver.userId());
        assertEquals("Có kiện hàng đang chờ bạn", receiver.title());
        assertEquals("Kiện hàng ORD-20261013-K7Q2ZD đang chờ bạn tại Tủ Thư viện FPTU (Tầng 1, Thư viện), ô số 4. "
                        + "Mã mở tủ: " + order.getPinCode() + ". Hạn lấy hàng: " + deadline + ". "
                        + "Nhập mã trên màn hình tủ để mở ô và lấy hàng.",
                receiver.message());
        assertTrue(order.getPinCode().matches(PIN_PATTERN));

        NotificationRequest sender = byType(sent, "ORDER_PARCEL_STORED");
        assertEquals(44L, sender.userId());
        assertEquals("Đã gửi hàng vào tủ", sender.title());
        assertTrue(sender.message().startsWith("Kiện hàng ORD-20261013-K7Q2ZD đã được cất tại Tủ Thư viện FPTU"
                + " (Tầng 1, Thư viện), ô số 4. Người nhận 0909000000 lấy hàng bằng mã " + order.getPinCode()
                + " trước " + deadline + ". "), sender.message());
        assertTrue(sender.message().endsWith(
                "Mã mở tủ đã được gửi tới người nhận qua thông báo trong ứng dụng Lock.R, SMS."), sender.message());

        ArgumentCaptor<GuestNotification.Request> guest = ArgumentCaptor.forClass(GuestNotification.Request.class);
        verify(notificationClient).notifyGuest(guest.capture());
        String sms = guest.getValue().smsMessage();
        assertEquals("Lock.R: Ban co kien hang ORD-20261013-K7Q2ZD tai Tu Thu vien FPTU (Tang 1, Thu vien), o so 4. "
                + "Ma mo tu: " + order.getPinCode() + ". Han lay: " + deadline + ".", sms);
        assertTrue(sms.length() <= OrderService.SMS_SINGLE_SEGMENT, sms.length() + " chars");
        assertTrue(sms.chars().allMatch(c -> c >= 0x20 && c <= 0x7E), sms);
        assertFalse(sms.contains("0909000000"), "SMS không cần lặp lại số người nhận");
        assertTrue(guest.getValue().emailBody().contains("Nơi lấy: Tủ Thư viện FPTU (Tầng 1, Thư viện), ô số 4"));
    }

    @Test
    void senderIsToldToShareTheCodeWhenNoChannelReachedTheReceiver() {
        OrderService service = service(false, false);
        LockerOrder order = storedSendOrder();
        when(orderRepository.findById(33L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(LockerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lockerLookupClient.getLockers(any())).thenThrow(new RuntimeException("locker-service down"));
        when(userClient.getUserByPhone("0909000000")).thenReturn(ApiResponse.ok(null));

        service.confirm(33L, 44L);

        NotificationRequest sender = byType(capturedNotifications(), "ORDER_PARCEL_STORED");
        assertTrue(sender.message().startsWith("Kiện hàng ORD-20261013-K7Q2ZD đã được cất tại tủ #5. "), sender.message());
        assertTrue(sender.message().contains("Lock.R CHƯA gửi được mã tới người nhận"), sender.message());
        assertTrue(sender.message().contains("\"Chia sẻ mã cho người nhận\""), sender.message());
        assertTrue(capturedNotifications().stream().noneMatch(n -> "ORDER_PARCEL_READY".equals(n.type())));
        verify(notificationClient, never()).notifyGuest(any());
    }

    @Test
    void deadlineIsShownInVietnamTime() {
        OrderService service = service(false, false);

        assertEquals("09:12 14/10/2026", service.formatDeadline(LocalDateTime.of(2026, 10, 14, 2, 12)));
        assertEquals("06:30 15/10/2026", service.formatDeadline(LocalDateTime.of(2026, 10, 14, 23, 30, 59)));
        assertEquals("chưa xác định", service.formatDeadline(null));
    }

    @Test
    void smsDropsAddressThenShortensLockerNameToStayInOneSegment() {
        var longAddress = new OrderService.PickupPlace("Tủ Thư viện FPTU",
                "Lô E2a-7, Đường D1, Khu Công nghệ cao, Phường Long Thạnh Mỹ, TP. Thủ Đức, TP. Hồ Chí Minh", 12);
        String sms = OrderService.pickupSms("ORD-20261013-K7Q2ZD", longAddress, "482913", "09:12 14/10/2026");
        assertEquals("Lock.R: Ban co kien hang ORD-20261013-K7Q2ZD tai Tu Thu vien FPTU, o so 12. "
                + "Ma mo tu: 482913. Han lay: 09:12 14/10/2026.", sms);

        var longName = new OrderService.PickupPlace(
                "Tủ gửi hàng thông minh Lock.R – Thư viện Đại học FPT cơ sở Thành phố Hồ Chí Minh dãy A tầng trệt", null, 3);
        String shortened = OrderService.pickupSms("ORD-20261013-K7Q2ZD", longName, "482913", "09:12 14/10/2026");
        assertTrue(shortened.length() <= OrderService.SMS_SINGLE_SEGMENT, shortened.length() + " chars");
        assertTrue(shortened.startsWith("Lock.R: Ban co kien hang ORD-20261013-K7Q2ZD tai Tu gui hang thong minh Lock.R - "),
                shortened);
        assertTrue(shortened.endsWith(", o so 3. Ma mo tu: 482913. Han lay: 09:12 14/10/2026."), shortened);
    }

    @Test
    void smsWithoutBoxOrLockerLookupStillCarriesPinAndDeadline() {
        String sms = OrderService.pickupSms("ORD-1", new OrderService.PickupPlace("tủ #5", null, null),
                "482913", "chưa xác định");

        assertEquals("Lock.R: Ban co kien hang ORD-1 tai tu #5. Ma mo tu: 482913. Han lay: chua xac dinh.", sms);
    }

    @Test
    void smsTextStripsVietnameseMarksAndNonGsmCharacters() {
        assertEquals("Tu Do Thanh Dat - 'Quan 1'", SmsText.ascii("Tủ Đỗ Thành Đạt – ‘Quận 1’"));
        assertEquals("Phong A B", SmsText.ascii("Phòng  A 😀 B"));
        assertEquals("", SmsText.ascii(null));
    }

    private List<NotificationRequest> capturedNotifications() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient, atLeastOnce()).requestNotification(captor.capture());
        return captor.getAllValues();
    }

    private static NotificationRequest byType(List<NotificationRequest> sent, String type) {
        return sent.stream().filter(n -> type.equals(n.type())).findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " notification in " + sent));
    }

    private static LockerOrder storedSendOrder() {
        LockerOrder order = new LockerOrder();
        order.setId(33L);
        order.setUserId(44L);
        order.setOrderCode("ORD-20261013-K7Q2ZD");
        order.setType("SEND");
        order.setServiceCategory("SEND");
        order.setStatus("INITIALIZED");
        order.setSendBoxId(901L);
        order.setLockerId(5L);
        order.setPinCode("111111");
        order.setReceiverPhone("0909000000");
        order.setReceiverName("Người Nhận");
        order.setReceiverEmail("nhan@example.com");
        order.setTotalPrice(BigDecimal.valueOf(15000));
        order.setOriginalPrice(BigDecimal.valueOf(15000));
        order.setPaymentStatus("PAID");
        order.setDropOpenedAt(LocalDateTime.now().minusMinutes(1));
        order.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        return order;
    }
}
