package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.DroneJourneyEventResponse;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.dto.OrderMilestone;
import com.huynqb.laundrylocker.order.dto.OrderParcelView;
import com.huynqb.laundrylocker.order.dto.ReceiverOrderDetailResponse;
import com.huynqb.laundrylocker.order.dto.ReceiverOrderEmailResponse;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// "Gửi lại qua email" trong chi tiết đơn nhận: gửi tới email tài khoản người bấm, chỉ kèm mã mở tủ khi màn
/// chi tiết cũng cho họ thấy mã, giới hạn một lần mỗi phút, đơn không thuộc họ ⇒ 404.
class ReceiverOrderMailServiceTest {

    private static final long USER = 42L;
    private static final long ORDER = 900L;

    private final ReceiverOrderQueryService queries = mock(ReceiverOrderQueryService.class);
    private final UserClient users = mock(UserClient.class);
    private final NotificationClient notifications = mock(NotificationClient.class);
    private final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    private ReceiverOrderMailService service;
    private Instant now = Instant.parse("2026-10-31T03:00:00Z");

    @BeforeEach
    void setUp() {
        service = new ReceiverOrderMailService(queries, users, notifications, history);
        service.setClock(new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        });
        // DB lưu giờ UTC (container), email hiện giờ Việt Nam.
        service.setTime(new BusinessTime(ZoneOffset.UTC, ZoneId.of("Asia/Ho_Chi_Minh"), Clock.systemUTC()));
        when(notifications.notifyGuest(any()))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(false, true, false, true)));
    }

    private void account(String email) {
        when(users.getUser(USER))
                .thenReturn(ApiResponse.ok(new UserSummary(USER, email, "0901000002", "Minh Anh", "ACTIVE")));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ReceiverOrderDetailResponse detail(Map<String, Object> f) {
        return new ReceiverOrderDetailResponse(
                (Long) f.getOrDefault("id", null),
                (String) f.getOrDefault("orderCode", null),
                (String) f.getOrDefault("type", null),
                (String) f.getOrDefault("status", null),
                (String) f.getOrDefault("deliveryStage", null),
                (String) f.getOrDefault("viewerRole", null),
                (Long) f.getOrDefault("senderUserId", null),
                (String) f.getOrDefault("senderName", null),
                (String) f.getOrDefault("senderPhone", null),
                (String) f.getOrDefault("senderEmail", null),
                (String) f.getOrDefault("receiverName", null),
                (String) f.getOrDefault("receiverPhone", null),
                (String) f.getOrDefault("receiverEmail", null),
                (Long) f.getOrDefault("lockerId", null),
                (String) f.getOrDefault("lockerCode", null),
                (String) f.getOrDefault("lockerName", null),
                (String) f.getOrDefault("lockerAddress", null),
                (Double) f.getOrDefault("lockerLatitude", null),
                (Double) f.getOrDefault("lockerLongitude", null),
                (Long) f.getOrDefault("boxId", null),
                (Integer) f.getOrDefault("boxNumber", null),
                (String) f.getOrDefault("boxSize", null),
                (String) f.getOrDefault("boxType", null),
                (Long) f.getOrDefault("sourceLockerId", null),
                (String) f.getOrDefault("sourceLockerCode", null),
                (String) f.getOrDefault("sourceLockerName", null),
                (String) f.getOrDefault("sourceLockerAddress", null),
                (Integer) f.getOrDefault("routeDistanceMeters", null),
                (LocalDateTime) f.getOrDefault("pickupDeadline", null),
                (Boolean) f.getOrDefault("overdue", false),
                (Boolean) f.getOrDefault("readyForPickup", false),
                (String) f.getOrDefault("pickupPin", null),
                (String) f.getOrDefault("qrToken", null),
                (LocalDateTime) f.getOrDefault("intendedReceiveAt", null),
                (Integer) f.getOrDefault("rentalDurationHours", null),
                (String) f.getOrDefault("deliveryAddress", null),
                (String) f.getOrDefault("paymentStatus", null),
                (Boolean) f.getOrDefault("delegated", false),
                (String) f.getOrDefault("delegateName", null),
                (String) f.getOrDefault("delegatePhone", null),
                (String) f.getOrDefault("delegateNote", null),
                (LocalDateTime) f.getOrDefault("delegatedAt", null),
                (String) f.getOrDefault("customerNote", null),
                (OrderParcelView) f.getOrDefault("parcel", null),
                (String) f.getOrDefault("deliveryBlockReason", null),
                (String) f.getOrDefault("deliveryBlockReasonLabel", null),
                (LocalDateTime) f.getOrDefault("deliveryBlockedAt", null),
                (String) f.getOrDefault("failureReason", null),
                (String) f.getOrDefault("failureReasonLabel", null),
                (LocalDateTime) f.getOrDefault("failedAt", null),
                (LocalDateTime) f.getOrDefault("parcelReturnedAt", null),
                (Integer) f.getOrDefault("etaMinutes", null),
                (Boolean) f.getOrDefault("droneTrackingAvailable", false),
                (List) f.getOrDefault("journey", List.of()),
                (LocalDateTime) f.getOrDefault("createdAt", null),
                (LocalDateTime) f.getOrDefault("paidAt", null),
                (LocalDateTime) f.getOrDefault("updatedAt", null),
                (LocalDateTime) f.getOrDefault("completedAt", null),
                (List) f.getOrDefault("timeline", List.of()));
    }

    private static Map<String, Object> ready() {
        Map<String, Object> f = new HashMap<>();
        f.put("id", ORDER);
        f.put("orderCode", "ORD-20261031-ABC123");
        f.put("type", "SEND");
        f.put("status", "STORING");
        f.put("viewerRole", ReceiverOrderQueryService.VIEWER_RECEIVER);
        f.put("senderName", "Quốc Bảo");
        f.put("receiverName", "Minh Anh");
        f.put("receiverEmail", "nhap.boi.nguoi.gui@example.com");
        f.put("lockerName", "Tủ Landmark 81");
        f.put("lockerAddress", "720A Điện Biên Phủ");
        f.put("boxNumber", 4);
        f.put("readyForPickup", true);
        f.put("pickupPin", "795296");
        f.put("pickupDeadline", LocalDateTime.of(2026, 11, 1, 4, 25));
        return f;
    }

    private GuestNotification.Request sentRequest() {
        ArgumentCaptor<GuestNotification.Request> sent = ArgumentCaptor.forClass(GuestNotification.Request.class);
        verify(notifications).notifyGuest(sent.capture());
        return sent.getValue();
    }

    @Test
    void hangDaVaoTu_guiEmailTaiKhoanKemMaMoTuVaHanLay() {
        account("minhanh@example.com");
        when(queries.detail(USER, ORDER)).thenReturn(detail(ready()));

        ReceiverOrderEmailResponse result = service.resend(USER, ORDER);

        GuestNotification.Request sent = sentRequest();
        assertEquals("minhanh@example.com", sent.email());
        assertNull(sent.phone());
        String body = sent.emailBody();
        assertTrue(body.contains("ORD-20261031-ABC123"), body);
        assertTrue(body.contains("Mã mở tủ: 795296"), body);
        assertTrue(body.contains("Tủ Landmark 81 (720A Điện Biên Phủ), ô số 4"), body);
        assertTrue(body.contains("Hạn lấy hàng: 11:25 01/11/2026"), body);
        assertTrue(result.includesPickupCode());
        assertEquals("mi***@example.com", result.maskedEmail());
        verify(history).save(any(OrderStatusHistory.class));
    }

    @Test
    void chuaVaoTu_guiThongTinKhongCoMa() {
        account("minhanh@example.com");
        Map<String, Object> f = ready();
        f.put("status", "INITIALIZED");
        f.put("readyForPickup", false);
        f.put("pickupPin", null);
        when(queries.detail(USER, ORDER)).thenReturn(detail(f));

        ReceiverOrderEmailResponse result = service.resend(USER, ORDER);

        String body = sentRequest().emailBody();
        assertFalse(body.contains("Mã mở tủ:"), body);
        assertTrue(body.contains("Mã mở tủ sẽ được gửi cho bạn khi hàng đã vào tủ."), body);
        assertFalse(result.includesPickupCode());
    }

    @Test
    void taiKhoanChuaCoEmail_dungEmailNguoiGuiNhapChoNguoiNhan() {
        account(null);
        when(queries.detail(USER, ORDER)).thenReturn(detail(ready()));

        service.resend(USER, ORDER);

        assertEquals("nhap.boi.nguoi.gui@example.com", sentRequest().email());
    }

    @Test
    void nguoiLayHoChuaCoEmail_baoBoSungEmail() {
        account(null);
        Map<String, Object> f = ready();
        f.put("viewerRole", ReceiverOrderQueryService.VIEWER_DELEGATE);
        when(queries.detail(USER, ORDER)).thenReturn(detail(f));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.resend(USER, ORDER));
        assertEquals("RECEIVER_EMAIL_MISSING", ex.getCode());
        verify(notifications, never()).notifyGuest(any());
    }

    @Test
    void bamLaiTrongMotPhut_biChan_sauMotPhutGuiDuoc() {
        account("minhanh@example.com");
        when(queries.detail(USER, ORDER)).thenReturn(detail(ready()));
        service.resend(USER, ORDER);

        now = now.plusSeconds(20);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.resend(USER, ORDER));
        assertEquals("RECEIVER_EMAIL_TOO_SOON", ex.getCode());

        now = now.plusSeconds(41);
        service.resend(USER, ORDER);
    }

    @Test
    void guiThatBai_baoLoiVaKhongTinhLuotChan() {
        account("minhanh@example.com");
        when(queries.detail(USER, ORDER)).thenReturn(detail(ready()));
        when(notifications.notifyGuest(any()))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(false, false, false, false)))
                .thenReturn(ApiResponse.ok(new GuestNotification.Result(false, true, false, true)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.resend(USER, ORDER));
        assertEquals("RECEIVER_EMAIL_FAILED", ex.getCode());
        service.resend(USER, ORDER);
    }

    @Test
    void donKhongThuocNguoiBam_404VaKhongGuiGi() {
        when(queries.detail(USER, ORDER)).thenThrow(new NotFoundException("Order", ORDER));

        assertThrows(NotFoundException.class, () -> service.resend(USER, ORDER));
        verify(notifications, never()).notifyGuest(any());
    }

    // Giữ import cho các kiểu dùng trong ép kiểu của detail(..).
    @SuppressWarnings("unused")
    private static final List<Class<?>> TYPES =
            List.of(OrderParcelView.class, OrderMilestone.class, DroneJourneyEventResponse.class);
}
