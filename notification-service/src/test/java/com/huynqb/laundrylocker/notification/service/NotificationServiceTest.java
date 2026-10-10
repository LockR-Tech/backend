package com.huynqb.laundrylocker.notification.service;

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
                "notificationId", "5", "type", "SYSTEM", "referenceType", "ORDER", "referenceId", "42")));
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
