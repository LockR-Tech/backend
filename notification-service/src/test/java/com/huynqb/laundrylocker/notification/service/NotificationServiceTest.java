package com.huynqb.laundrylocker.notification.service;

import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.notification.dto.AdminBroadcastRequest;
import com.huynqb.laundrylocker.notification.dto.NotificationResponse;
import com.huynqb.laundrylocker.notification.model.FcmToken;
import com.huynqb.laundrylocker.notification.model.NotificationMessage;
import com.huynqb.laundrylocker.notification.repository.FcmTokenRepository;
import com.huynqb.laundrylocker.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private FcmTokenRepository fcmTokenRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private FcmPushNotificationService fcmPushNotificationService;
    @Mock private WebSocketNotificationService webSocketNotificationService;

    @InjectMocks private NotificationService service;

    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        when(notificationRepository.save(any(NotificationMessage.class))).thenAnswer(invocation -> {
            NotificationMessage saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(ids.incrementAndGet());
            }
            return saved;
        });
    }

    @Test
    @DisplayName("người dùng đánh dấu đã đọc notification của chính mình")
    void ownerCanMarkOwnNotificationRead() {
        NotificationMessage own = notification(5L, 7L);
        when(notificationRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(own));

        NotificationResponse result = service.markRead(5L, 7L);

        assertThat(result.isRead()).isTrue();
        assertThat(result.status()).isEqualTo("READ");
        assertThat(result.readAt()).isNotNull();
    }

    @Test
    @DisplayName("notification của người khác trả 404 khi đánh dấu đã đọc và không bị sửa")
    void markReadOnlyActsOnCallersOwnNotification() {
        when(notificationRepository.findByIdAndUserId(5L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(5L, 8L))
                .isInstanceOf(NotFoundException.class)
                .satisfies(ex -> assertThat(((NotFoundException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("đánh dấu theo lô chỉ áp dụng cho notification của người gọi")
    void batchReadOnlyActsOnCallersOwnNotifications() {
        when(notificationRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(notification(5L, 7L)));
        when(notificationRepository.findByIdAndUserId(6L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markBatchRead(List.of(5L, 6L), 7L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("người dùng xoá notification của chính mình; của người khác trả 404")
    void deleteOnlyActsOnCallersOwnNotification() {
        NotificationMessage own = notification(5L, 7L);
        when(notificationRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(own));
        when(notificationRepository.findByIdAndUserId(5L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(5L, 8L)).isInstanceOf(NotFoundException.class);
        verify(notificationRepository, never()).delete(any(NotificationMessage.class));

        service.delete(5L, 7L);
        verify(notificationRepository).delete(own);
    }

    @Test
    @DisplayName("admin đánh dấu đã đọc và xoá notification của bất kỳ ai; id không tồn tại trả 404")
    void adminActionsIgnoreOwnerAndReturn404WhenMissing() {
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification(5L, 7L)));
        when(notificationRepository.findById(9L)).thenReturn(Optional.empty());
        when(notificationRepository.existsById(5L)).thenReturn(true);
        when(notificationRepository.existsById(9L)).thenReturn(false);

        assertThat(service.markRead(5L).status()).isEqualTo("READ");
        assertThatThrownBy(() -> service.markRead(9L)).isInstanceOf(NotFoundException.class);

        service.delete(5L);
        verify(notificationRepository).deleteById(5L);
        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(NotFoundException.class);
        verify(notificationRepository, never()).deleteById(9L);
    }

    @Test
    @DisplayName("gửi lại đẩy FCM cho đúng người nhận với data gốc, không tạo bản ghi mới")
    void resendPushesToNotificationOwner() {
        NotificationMessage existing = notification(5L, 7L);
        existing.setReferenceId(42L);
        existing.setReferenceType("ORDER");
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(notificationRepository.findById(9L)).thenReturn(Optional.empty());

        NotificationResponse result = service.resend(5L);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.isRead()).isFalse();
        verify(fcmPushNotificationService).sendToUser(eq(7L), eq("Tiêu đề"), eq("Nội dung"), eq(Map.of(
                "notificationId", "5",
                "type", "SYSTEM",
                "referenceType", "ORDER",
                "referenceId", "42",
                "title", "Tiêu đề",
                "body", "Nội dung",
                "content", "Nội dung")));
        verify(notificationRepository, never()).save(any());
        assertThatThrownBy(() -> service.resend(9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("broadcast có userIds chỉ tạo/gửi cho các user đó, kể cả user chưa có FCM token")
    void broadcastWithUserIdsTargetsOnlyThoseUsers() {
        List<NotificationResponse> result = service.adminBroadcast(
                new AdminBroadcastRequest("Bảo trì", "Tủ tạm dừng", null, null, null, Arrays.asList(3L, 4L, 3L, null)));

        assertThat(result).extracting(NotificationResponse::userId).containsExactly(3L, 4L);
        assertThat(result).allSatisfy(n -> assertThat(n.type()).isEqualTo("SYSTEM"));
        verify(notificationRepository, times(2)).save(any(NotificationMessage.class));
        verify(fcmPushNotificationService).sendToUser(eq(3L), eq("Bảo trì"), eq("Tủ tạm dừng"), anyMap());
        verify(fcmPushNotificationService).sendToUser(eq(4L), eq("Bảo trì"), eq("Tủ tạm dừng"), anyMap());
        verify(fcmPushNotificationService, never()).broadcast(anyString(), anyString(), anyMap());
        verify(webSocketNotificationService, never()).broadcast(any());
        verify(fcmTokenRepository, never()).findAll();
    }

    @Test
    @DisplayName("broadcast có userIds rỗng không gửi cho ai")
    void broadcastWithEmptyUserIdsSendsNothing() {
        assertThat(service.adminBroadcast(new AdminBroadcastRequest("A", "B", null, null, null, List.of()))).isEmpty();

        verify(notificationRepository, never()).save(any());
        verify(fcmPushNotificationService, never()).broadcast(anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("broadcast không có userIds gửi mọi user đã đăng ký FCM token, mỗi user đúng một push")
    void broadcastWithoutUserIdsSendsExactlyOnePushPerRegisteredUser() {
        // User 3 có hai thiết bị: vẫn chỉ một lần gửi cho user (FCM tự multicast tới các token của user).
        when(fcmTokenRepository.findAll()).thenReturn(List.of(token(3L, "a"), token(4L, "b"), token(3L, "c")));

        List<NotificationResponse> result =
                service.adminBroadcast(new AdminBroadcastRequest("A", "B", "PROMO", null, null, null));

        assertThat(result).extracting(NotificationResponse::userId).containsExactly(3L, 4L);
        verify(fcmPushNotificationService, times(1)).sendToUser(eq(3L), eq("A"), eq("B"), anyMap());
        verify(fcmPushNotificationService, times(1)).sendToUser(eq(4L), eq("A"), eq("B"), anyMap());
        verify(fcmPushNotificationService, times(2)).sendToUser(anyLong(), anyString(), anyString(), anyMap());
        verify(fcmPushNotificationService, never()).broadcast(anyString(), anyString(), anyMap());
        // WebSocket giữ nguyên: tin riêng cho từng user + bản tin trên kênh chung.
        verify(webSocketNotificationService).sendToUser(eq(3L), any());
        verify(webSocketNotificationService).sendToUser(eq(4L), any());
        verify(webSocketNotificationService, times(2)).broadcast(any());
    }

    @Test
    @DisplayName("sự kiện đơn không kèm message: chỉ cập nhật realtime, không tạo thông báo tiếng Anh trùng")
    void orderEventWithoutMessageOnlyPushesRealtimeUpdate() {
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.ORDER_CREATED, "order-service",
                Map.of("orderId", 21L, "userId", 7L, "status", "INITIALIZED")));
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.ORDER_STATUS_CHANGED, "order-service",
                Map.of("orderId", 21L, "userId", 7L, "status", "STORING")));
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.PAYMENT_COMPLETED, "payment-service",
                Map.of("paymentId", 3L, "orderId", 21L, "userId", 7L, "amount", 15000)));

        verify(notificationRepository, never()).save(any(NotificationMessage.class));
        verify(fcmPushNotificationService, never()).sendToUser(anyLong(), anyString(), anyString(), anyMap());
        verify(webSocketNotificationService, times(3)).sendOrderUpdate(eq(7L), anyMap());
    }

    @Test
    @DisplayName("nạp ví thành công: thông báo tiếng Việt kèm số tiền, tham chiếu giao dịch ví")
    void topUpCompletedIsNotifiedInVietnamese() {
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.PAYMENT_COMPLETED, "payment-service",
                Map.of("paymentId", 55L, "orderId", 0L, "userId", 7L, "amount", 100000)));

        NotificationMessage saved = savedNotification();
        assertThat(saved.getTitle()).isEqualTo("Nạp ví thành công");
        assertThat(saved.getMessage()).isEqualTo("Đã cộng 100.000đ vào ví LockR.");
        assertThat(saved.getReferenceType()).isEqualTo("WALLET");
        assertThat(saved.getReferenceId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("thanh toán đơn lỗi: thông báo tiếng Việt, mở được đơn liên quan")
    void orderPaymentFailedIsNotifiedInVietnamese() {
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.PAYMENT_FAILED, "payment-service",
                Map.of("paymentId", 56L, "orderId", 21L, "userId", 7L, "amount", "15000.00")));

        NotificationMessage saved = savedNotification();
        assertThat(saved.getTitle()).isEqualTo("Thanh toán thất bại");
        assertThat(saved.getMessage()).contains("15.000đ").contains("đơn #21").doesNotContain("event received");
        assertThat(saved.getReferenceType()).isEqualTo("ORDER");
        assertThat(saved.getReferenceId()).isEqualTo(21L);
    }

    @Test
    @DisplayName("sự kiện kèm message (phiếu sự cố) giữ nguyên nội dung gửi kèm")
    void eventWithMessageKeepsItsMessage() {
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.LOCKER_REPORT_RESOLVED, "locker-service",
                Map.of("userId", 7L, "referenceId", 9L, "referenceType", "LOCKER_REPORT",
                        "message", "Báo cáo lỗi tủ A của bạn đã được xử lý xong.")));

        NotificationMessage saved = savedNotification();
        assertThat(saved.getTitle()).isEqualTo("Báo cáo đã được xử lý xong");
        assertThat(saved.getMessage()).isEqualTo("Báo cáo lỗi tủ A của bạn đã được xử lý xong.");
        assertThat(saved.getReferenceType()).isEqualTo("LOCKER_REPORT");
    }

    @Test
    @DisplayName("tủ đổi số ô trống: đẩy mã tủ lên topic công khai, không tạo thông báo")
    void lockerAvailabilityChangeIsRelayedWithoutNotification() {
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.LOCKER_AVAILABILITY_CHANGED, "locker-service",
                Map.of("lockerId", 7L)));

        org.mockito.ArgumentCaptor<Object> payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(webSocketNotificationService).sendToDestination(
                eq("/topic/public/locker-availability"), payload.capture());
        @SuppressWarnings("unchecked") Map<String, Object> sent = (Map<String, Object>) payload.getValue();
        assertThat(sent).containsEntry("lockerId", 7L).containsOnlyKeys("lockerId", "timestamp");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("ví/điểm đổi không kèm message: chỉ nhắc app chủ tài khoản tải lại, không tạo thông báo")
    void silentWalletAndLoyaltyChangesOnlyPushUserEvent() {
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.WALLET_UPDATED, "payment-service",
                Map.of("userId", 7L, "referenceType", "WALLET")));
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.LOYALTY_POINTS_CHANGED, "loyalty-service",
                Map.of("userId", 7L, "referenceType", "LOYALTY")));

        org.mockito.ArgumentCaptor<Object> payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(webSocketNotificationService, times(2)).sendUserEvent(eq(7L), payload.capture());
        @SuppressWarnings("unchecked") Map<String, Object> first = (Map<String, Object>) payload.getAllValues().get(0);
        assertThat(first).containsEntry("type", DomainEventNames.WALLET_UPDATED).containsEntry("referenceType", "WALLET");
        verify(notificationRepository, never()).save(any());
        verify(webSocketNotificationService, never()).sendOrderUpdate(anyLong(), any());
    }

    @Test
    @DisplayName("admin duyệt rút tiền: thông báo theo tiêu đề gửi kèm, tham chiếu ví (không vào kênh đơn)")
    void walletEventWithMessageIsNotifiedWithItsTitle() {
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.WALLET_UPDATED, "payment-service",
                Map.of("userId", 7L, "referenceType", "WALLET", "referenceId", 55L,
                        "title", "Rút tiền thành công", "message", "Yêu cầu rút 50000 đ đã được chuyển khoản.")));

        NotificationMessage saved = savedNotification();
        assertThat(saved.getTitle()).isEqualTo("Rút tiền thành công");
        assertThat(saved.getType()).isEqualTo(DomainEventNames.WALLET_UPDATED);
        assertThat(saved.getReferenceType()).isEqualTo("WALLET");
        assertThat(saved.getReferenceId()).isEqualTo(55L);
        verify(webSocketNotificationService).sendToUser(eq(7L), any());
        verify(webSocketNotificationService, never()).sendOrderUpdate(anyLong(), any());
        verify(webSocketNotificationService, never()).sendUserEvent(anyLong(), any());
    }

    @Test
    @DisplayName("admin sửa khuyến mãi/cửa hàng/tủ: chỉ đẩy loại + mã lên topic công khai")
    void catalogChangeIsRelayedToPublicTopic() {
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.CATALOG_CHANGED, "order-service",
                Map.of("entity", "PROMOTION", "id", 12L)));

        org.mockito.ArgumentCaptor<Object> payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(webSocketNotificationService).sendToDestination(eq("/topic/public/catalog"), payload.capture());
        @SuppressWarnings("unchecked") Map<String, Object> sent = (Map<String, Object>) payload.getValue();
        assertThat(sent).containsEntry("entity", "PROMOTION").containsEntry("id", 12L)
                .containsOnlyKeys("entity", "id", "timestamp");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("việc bảo trì/thiết bị đổi: chỉ đẩy loại + mã lên topic nhân viên, không tạo thông báo")
    void maintenanceAndDeviceChangesAreRelayedToStaffTopic() {
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.MAINTENANCE_CHANGED, "locker-service",
                Map.of("entity", "REPORT", "id", 34L, "lockerId", 5L)));
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.IOT_DEVICE_STATUS_CHANGED, "iot-service",
                Map.of("deviceId", "PI-01", "lockerId", 5L, "status", "OFFLINE")));

        org.mockito.ArgumentCaptor<Object> payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(webSocketNotificationService, times(2))
                .sendToDestination(eq("/topic/staff/maintenance"), payload.capture());
        @SuppressWarnings("unchecked") Map<String, Object> report = (Map<String, Object>) payload.getAllValues().get(0);
        assertThat(report).containsEntry("entity", "REPORT").containsEntry("id", 34L).containsEntry("lockerId", 5L)
                .containsOnlyKeys("entity", "id", "lockerId", "timestamp");
        @SuppressWarnings("unchecked") Map<String, Object> device = (Map<String, Object>) payload.getAllValues().get(1);
        assertThat(device).containsEntry("entity", "DEVICE").containsEntry("id", "PI-01");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("dữ liệu admin đổi (người dùng, hoàn tiền…) và ví/điểm: đẩy loại + mã lên topic admin")
    void adminDataAndAccountChangesAreRelayedToAdminTopic() {
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.ADMIN_DATA_CHANGED, "payment-service",
                Map.of("entity", "REFUND", "id", 9L)));
        service.consumeDomainEvent(DomainEvent.of(DomainEventNames.WALLET_UPDATED, "payment-service",
                Map.of("userId", 7L, "referenceType", "WALLET")));

        org.mockito.ArgumentCaptor<Object> payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(webSocketNotificationService, times(2))
                .sendToDestination(eq("/topic/admin/changes"), payload.capture());
        @SuppressWarnings("unchecked") Map<String, Object> refund = (Map<String, Object>) payload.getAllValues().get(0);
        assertThat(refund).containsEntry("entity", "REFUND").containsEntry("id", 9L)
                .containsOnlyKeys("entity", "id", "timestamp");
        @SuppressWarnings("unchecked") Map<String, Object> wallet = (Map<String, Object>) payload.getAllValues().get(1);
        assertThat(wallet).containsEntry("entity", "WALLET").containsEntry("id", 7L);
        // Ví vẫn nhắc app của chủ ví như trước; tin admin không tạo thông báo.
        verify(webSocketNotificationService).sendUserEvent(eq(7L), any());
        verify(notificationRepository, never()).save(any());
    }

    private NotificationMessage savedNotification() {
        org.mockito.ArgumentCaptor<NotificationMessage> captor =
                org.mockito.ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    private static NotificationMessage notification(Long id, Long userId) {
        NotificationMessage notification = new NotificationMessage();
        notification.setId(id);
        notification.setUserId(userId);
        notification.setTitle("Tiêu đề");
        notification.setMessage("Nội dung");
        return notification;
    }

    private static FcmToken token(Long userId, String value) {
        FcmToken token = new FcmToken();
        token.setUserId(userId);
        token.setToken("token-" + value);
        return token;
    }
}
